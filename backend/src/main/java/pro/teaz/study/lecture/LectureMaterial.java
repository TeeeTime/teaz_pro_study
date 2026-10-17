package pro.teaz.study.lecture;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import pro.teaz.study.ai.StudyAi.LectureSource;
import pro.teaz.study.ai.StudyAi.MaterialPart;
import pro.teaz.study.course.SourceFile;
import pro.teaz.study.course.SourceFileRepository;
import pro.teaz.study.storage.FileStorage;

/**
 * A lecture's own share of the course material: the PDF page ranges the plan
 * assigned to it, cut out into small PDFs, plus its images. Page generation and
 * the chat send only this, not the whole script.
 */
@Component
public class LectureMaterial {

	private final SourceFileRepository files;
	private final FileStorage storage;
	private final JsonMapper json;

	public LectureMaterial(SourceFileRepository files, FileStorage storage, JsonMapper json) {
		this.files = files;
		this.storage = storage;
		this.json = json;
	}

	public List<LectureSource> sources(Lecture lecture) {
		return List.of(json.readValue(lecture.getSources(), LectureSource[].class));
	}

	public List<MaterialPart> parts(Lecture lecture) {
		List<MaterialPart> parts = new ArrayList<>();
		for (LectureSource source : sources(lecture)) {
			SourceFile file = files.findById(source.fileId()).orElse(null);
			if (file == null) continue;
			if (file.isPdf() && source.fromPage() != null) {
				parts.add(new MaterialPart(
						file.getOriginalName() + ", pages " + source.fromPage() + "-" + source.toPage(),
						SourceFile.PDF, storage.extractPages(file.getStoragePath(), source.fromPage(), source.toPage())));
			} else {
				parts.add(new MaterialPart(file.getOriginalName(), file.getMediaType(), storage.read(file.getStoragePath())));
			}
		}
		return parts;
	}
}
