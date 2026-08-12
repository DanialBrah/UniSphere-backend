package com.unisphere.backend.campus.lostfound.entity;

import com.unisphere.backend.campus.lostfound.enums.LostFoundClaimStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Someone asserting that a reported item is theirs.
 *
 * <p>Shaped after {@code CommunityJoinRequest}, including the deliberate absence of a unique
 * constraint on (itemId, claimantId): a rejected claimant may re-apply, so history must be
 * preservable. "At most one PENDING claim per (item, claimant)" is enforced in
 * {@code LostFoundClaimService} instead.
 *
 * <p>No {@code deletedAt} and therefore no {@code @SQLRestriction} — claims are audit history and
 * are never soft-deleted. Withdrawal is {@link LostFoundClaimStatus#CANCELLED}, which is also why
 * this entity has an {@code updatedAt} that {@code CommunityJoinRequest} does not: cancelling is a
 * claimant action, not a review.
 */
@Entity
@Table(name = "lost_found_claims")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class LostFoundClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "claimant_id", nullable = false)
    private Long claimantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private LostFoundClaimStatus status = LostFoundClaimStatus.PENDING;

    /**
     * The claimant's proof of ownership, adjudicated by the reporter against
     * {@code LostFoundItem.identifyingDetail} — which the claimant never sees at any claim status.
     */
    @Column(name = "proof_text", nullable = false, length = 1000)
    private String proofText;

    /** Bare object key — never a resolved URL; see changeset 012. */
    @Column(name = "proof_image_key", length = 500)
    private String proofImageKey;

    /** Free-text reason attached by whoever decided the claim. */
    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    /** Null while PENDING, and after a claimant-driven CANCELLED — nobody reviewed it. */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
