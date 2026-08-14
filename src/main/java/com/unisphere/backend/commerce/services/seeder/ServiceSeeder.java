package com.unisphere.backend.commerce.services.seeder;

import com.unisphere.backend.commerce.services.entity.ServiceListing;
import com.unisphere.backend.commerce.services.entity.ServiceOrder;
import com.unisphere.backend.commerce.services.entity.ServiceReview;
import com.unisphere.backend.commerce.services.enums.ServiceDeliveryMode;
import com.unisphere.backend.commerce.services.enums.ServiceListingStatus;
import com.unisphere.backend.commerce.services.enums.ServiceOrderStatus;
import com.unisphere.backend.commerce.services.enums.ServicePricingType;
import com.unisphere.backend.commerce.services.repository.ServiceListingRepository;
import com.unisphere.backend.commerce.services.repository.ServiceOrderRepository;
import com.unisphere.backend.commerce.services.repository.ServiceReviewRepository;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Seeds sample service listings, orders and reviews. Runs after ProjectSeeder (@Order(9)). Guard:
 * skips entirely if any listing already exists.
 *
 * <p>Only {@code student@unisphere.dev}, {@code alumni@unisphere.dev} and
 * {@code club@unisphere.dev} are valid providers among the seeded accounts, and
 * {@code employer@unisphere.dev} demonstrates a non-provider role ordering a service (see
 * {@code ServiceAccessService.assertCanOrder} — everyone but ADMIN may order). No conversations/
 * messages are seeded here — {@code ServiceOrderService.createOrder} always creates one live, so a
 * seeded order intentionally leaves {@code conversationId} null rather than faking messaging state.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeding.enabled", havingValue = "true", matchIfMissing = false)
public class ServiceSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ServiceListingRepository listingRepository;
    private final ServiceOrderRepository orderRepository;
    private final ServiceReviewRepository reviewRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (listingRepository.count() > 0) {
            log.info("Services already seeded — skipping.");
            return;
        }

        Optional<User> studentOpt  = userRepository.findByEmail("student@unisphere.dev");
        Optional<User> alumniOpt   = userRepository.findByEmail("alumni@unisphere.dev");
        Optional<User> employerOpt = userRepository.findByEmail("employer@unisphere.dev");

        if (studentOpt.isEmpty() || alumniOpt.isEmpty() || employerOpt.isEmpty()) {
            log.warn("ServiceSeeder: seeded users not found — run UserSeeder first (app.seeding.enabled=true).");
            return;
        }

        User student  = studentOpt.get();
        User alumni   = alumniOpt.get();
        User employer = employerOpt.get();

        // ── ACTIVE, FIXED price — the straightforward order-and-accept demo ────
        ServiceListing tutoring = baseListing(student, "Calculus & Linear Algebra Tutoring",
                "One-on-one tutoring sessions covering calculus and linear algebra fundamentals.",
                "Tutoring", ServicePricingType.FIXED, new BigDecimal("40.00"), ServiceDeliveryMode.ONLINE);

        // ── ACTIVE, HOURLY price ──────────────────────────────────────────────
        ServiceListing design = baseListing(alumni, "Logo & Brand Identity Design",
                "Professional logo design and brand identity packages for clubs and student projects.",
                "Design", ServicePricingType.HOURLY, new BigDecimal("25.00"), ServiceDeliveryMode.BOTH);

        // ── ACTIVE, NEGOTIABLE price — the negotiate-then-order demo ────────────
        ServiceListing editing = baseListing(alumni, "Thesis & Essay Editing",
                "Proofreading and structural editing for theses, essays and reports.",
                "Writing", ServicePricingType.NEGOTIABLE, null, ServiceDeliveryMode.ONLINE);

        // ── PAUSED — visible only to the provider/admin ──────────────────────
        ServiceListing photography = baseListing(student, "Event Photography (Paused)",
                "Photography coverage for campus events. Currently paused for exam season.",
                "Photography", ServicePricingType.FIXED, new BigDecimal("80.00"), ServiceDeliveryMode.PHYSICAL);
        photography.setStatus(ServiceListingStatus.PAUSED);

        listingRepository.saveAll(List.of(tutoring, design, editing, photography));

        // ── Orders on the two ACTIVE/FIXED-or-HOURLY listings ────────────────────
        ServiceOrder completedOrder = order(tutoring, employer, ServiceOrderStatus.COMPLETED,
                "Weekly tutoring sessions for a colleague's child preparing for university entrance exams.",
                tutoring.getPrice());
        ServiceOrder acceptedOrder = order(design, employer, ServiceOrderStatus.ACCEPTED,
                "Need a new logo for our internship program's recruiting materials.",
                new BigDecimal("150.00"));
        ServiceOrder pendingOrder = order(editing, employer, ServiceOrderStatus.PENDING,
                "Editing needed for a 40-page internal report. Budget to be discussed.",
                null);

        orderRepository.saveAll(List.of(completedOrder, acceptedOrder, pendingOrder));

        // ── Review on the COMPLETED order — rolls into tutoring's rating ─────────
        ServiceReview review = new ServiceReview();
        review.setOrderId(completedOrder.getId());
        review.setReviewerId(employer.getId());
        review.setRevieweeId(student.getId());
        review.setRating(5);
        review.setComment("Excellent tutor — patient, clear explanations, highly recommended.");
        reviewRepository.save(review);

        tutoring.setRatingAvg(new BigDecimal("5.00"));
        tutoring.setRatingCount(1);
        listingRepository.save(tutoring);

        log.info("Seeded 4 service listings, 3 orders and 1 review.");
    }

    private ServiceListing baseListing(User provider, String title, String description, String category,
                                       ServicePricingType pricingType, BigDecimal price, ServiceDeliveryMode deliveryMode) {
        ServiceListing listing = new ServiceListing();
        listing.setProviderId(provider.getId());
        listing.setTitle(title);
        listing.setDescription(description);
        listing.setCategory(category);
        listing.setPricingType(pricingType);
        listing.setPrice(price);
        listing.setDeliveryMode(deliveryMode);
        listing.setStatus(ServiceListingStatus.ACTIVE);
        return listing;
    }

    private ServiceOrder order(ServiceListing listing, User client, ServiceOrderStatus status,
                               String requirements, BigDecimal agreedPrice) {
        ServiceOrder order = new ServiceOrder();
        order.setListingId(listing.getId());
        order.setClientId(client.getId());
        order.setRequirements(requirements);
        order.setAgreedPrice(agreedPrice);
        order.setStatus(status);
        return order;
    }
}
