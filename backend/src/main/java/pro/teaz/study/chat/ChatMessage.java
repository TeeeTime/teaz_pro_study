package pro.teaz.study.chat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

/** One turn of a lecture's chat. Only text is kept; see ChatService for why. */
@Entity
public class ChatMessage {

	public enum Role {
		USER, ASSISTANT
	}

	public enum ProposalStatus {
		PENDING, APPLIED, DISCARDED
	}

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID lectureId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(nullable = false)
	private String text;

	/** JSON {summary, operations}: changes to the lecture's pages, awaiting the user's decision. */
	private String proposal;

	@Enumerated(EnumType.STRING)
	private ProposalStatus proposalStatus;

	@Column(nullable = false)
	private Instant createdAt;

	protected ChatMessage() {
	}

	public ChatMessage(UUID lectureId, Role role, String text, String proposal) {
		this.id = UUID.randomUUID();
		this.lectureId = lectureId;
		this.role = role;
		this.text = text;
		this.proposal = proposal;
		this.proposalStatus = proposal == null ? null : ProposalStatus.PENDING;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getLectureId() {
		return lectureId;
	}

	public Role getRole() {
		return role;
	}

	public String getText() {
		return text;
	}

	public String getProposal() {
		return proposal;
	}

	public ProposalStatus getProposalStatus() {
		return proposalStatus;
	}

	public void setProposalStatus(ProposalStatus proposalStatus) {
		this.proposalStatus = proposalStatus;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
