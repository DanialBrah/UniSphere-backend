package com.unisphere.backend.commerce.events.service;

import com.unisphere.backend.commerce.events.entity.Event;
import com.unisphere.backend.commerce.events.enums.EventRegistrationStatus;
import com.unisphere.backend.commerce.events.repository.EventRegistrationRepository;
import com.unisphere.backend.commerce.events.repository.EventRepository;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Notifies every REGISTERED attendee once, roughly {@code reminderHoursBefore} ahead of an event's
 * start. {@code Event.reminderSentAt} is what prevents a duplicate send across sweep ticks — the
 * window itself does not need to be tight.
 *
 * <p>Every-15-minutes cadence rather than {@code LostFoundExpiryScheduler}'s once-daily: a reminder
 * window is hour-granularity, not day-granularity, so it needs to catch an event crossing the
 * "starts within N hours" threshold reasonably promptly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventReminderScheduler {

    private final EventRepository eventRepository;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final NotificationService notificationService;

    @Value("${events.reminder-hours-before:24}")
    private int reminderHoursBefore;

    @Scheduled(cron = "${events.reminder-cron:0 */15 * * * *}", zone = "UTC")
    @Transactional
    public void sendReminders() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Event> due = eventRepository.findNeedingReminder(now, now.plusHours(reminderHoursBefore));
            for (Event event : due) {
                eventRegistrationRepository
                        .findUserIdsByEventIdAndStatus(event.getId(), EventRegistrationStatus.REGISTERED)
                        // actorId = null: a system notification, not a "someone did X to you" event.
                        .forEach(userId -> notificationService.createAndPush(
                                userId, null, NotificationType.EVENT, event.getId(), "EVENT_REMINDER"));
                event.setReminderSentAt(now);
            }
            if (!due.isEmpty()) {
                eventRepository.saveAll(due);
                log.info("Sent reminders for {} event(s)", due.size());
            }
        } catch (Exception ex) {
            log.warn("Scheduled event reminder sweep failed: {}", ex.getMessage());
        }
    }
}
