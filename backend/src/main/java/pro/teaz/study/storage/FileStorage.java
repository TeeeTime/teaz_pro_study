package pro.teaz.study.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PageExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import pro.teaz.study.StudyProperties;

/**
 * Uploaded material on disk, one folder per course: {@code <storageDir>/<courseId>/<fileId>}.
 * Paths stored in the database are relative to storageDir, so the folder can move.
 */
@Component
public class FileStorage {

	private final Path root;

	public FileStorage(StudyProperties properties) {
		this.root = properties.storageDir().toAbsolutePath().normalize();
	}

	/** @return path relative to the storage root, for SourceFile.storagePath */
	public String save(UUID courseId, UUID fileId, InputStream content) {
		Path relative = Path.of(courseId.toString(), fileId.toString());
		Path target = root.resolve(relative);
		try {
			Files.createDirectories(target.getParent());
			Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not store " + relative, e);
		}
		return relative.toString().replace('\\', '/');
	}

	public Path resolve(String storagePath) {
		Path path = root.resolve(storagePath).normalize();
		if (!path.startsWith(root)) throw new IllegalArgumentException("Path outside storage: " + storagePath);
		return path;
	}

	public byte[] read(String storagePath) {
		try {
			return Files.readAllBytes(resolve(storagePath));
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read " + storagePath, e);
		}
	}

	/** @throws InvalidPdfException if the file isn't a readable PDF */
	public int pageCount(String storagePath) {
		try (PDDocument document = Loader.loadPDF(resolve(storagePath).toFile())) {
			return document.getNumberOfPages();
		} catch (IOException e) {
			throw new InvalidPdfException(e);
		}
	}

	/**
	 * A new PDF with only pages {@code fromPage..toPage} (1-based, inclusive), so a
	 * lecture sends Claude its own slides instead of the whole script.
	 */
	public byte[] extractPages(String storagePath, int fromPage, int toPage) {
		try (PDDocument source = Loader.loadPDF(resolve(storagePath).toFile());
				PDDocument extract = new PageExtractor(source, fromPage, toPage).extract();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			extract.save(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not extract pages from " + storagePath, e);
		}
	}

	public void deleteCourse(UUID courseId) {
		Path dir = root.resolve(courseId.toString());
		if (!Files.exists(dir)) return;
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		} catch (IOException e) {
			throw new UncheckedIOException("Could not delete " + dir, e);
		}
	}

	public static class InvalidPdfException extends RuntimeException {
		InvalidPdfException(Throwable cause) {
			super("Not a readable PDF", cause);
		}
	}
}
