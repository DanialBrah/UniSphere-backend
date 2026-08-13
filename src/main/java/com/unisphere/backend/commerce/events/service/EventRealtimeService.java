package com.unisphere.backend.commerce.events.service;

import com.unisphere.backend.commerce.events.dto.response.EventSeatsResponse;
import com.unisphere.backend.commerce.events.entity.Event;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Broadcasts live seat-count updates to {@code /topic/events/{eventId}/seats} whenever
 * {@code registeredCount}/{@code waitlistedCount} changes — registration, cancellation, or waitlist
 * promotion. No REST endpoint backs this; it is push-only, and a client viewing an event's detail
 * page subscribes on mount the same way a chat view subscribes to
 * {@code /topic/conversation/{id}}. No new {@code WebSocketConfig} registration is needed —
 * {@code enableSimpleBroker("/topic", "/user")} already accepts any {@code /topic/**} destination.
 *
 * <p>Structural copy of {@code NotificationService}'s own
 * {@code NotificationCreatedEvent}/{@code @TransactionalEventListener(AFTER_COMMIT)} pattern:
 * publish an in-process event from inside the write transaction, and push over STOMP only after it
 * actually commits. Pushing eagerly would broadcast a seat count a subsequent rollback makes false.
 */
@Service
@RequiredArgsConstructor
public class EventRealtimeService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    record SeatUpdateEvent(Long eventId, int registeredCount, Integer maxCapacity, int waitlistedCount) {}

    public void publishSeatUpdate(Event event) {
        eventPublisher.publishEvent(new SeatUpdateEvent(
                event.getId(), event.getRegisteredCount(), event.getMaxCapacity(), event.getWaitlistedCount()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatUpdate(SeatUpdateEvent event) {
        Integer available = event.maxCapacity() == null
                ? null : Math.max(event.maxCapacity() - event.registeredCount(), 0);
        messagingTemplate.convertAndSend("/topic/events/" + event.eventId() + "/seats",
                new EventSeatsResponse(event.eventId(), event.registeredCount(), event.maxCapacity(),
                        available, event.waitlistedCount()));
    }
}
