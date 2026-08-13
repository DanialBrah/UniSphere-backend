package com.unisphere.backend.commerce.events.controller;

import com.unisphere.backend.commerce.events.dto.request.CreateEventRequest;
import com.unisphere.backend.commerce.events.dto.request.EventStatusUpdateRequest;
import com.unisphere.backend.commerce.events.dto.request.UpdateEventRequest;
import com.unisphere.backend.commerce.events.dto.response.EventMapPinResponse;
import com.unisphere.backend.commerce.events.dto.response.EventNearbyResponse;
import com.unisphere.backend.commerce.events.dto.response.EventResponse;
import com.unisphere.backend.commerce.events.dto.response.EventStatsResponse;
import com.unisphere.backend.commerce.events.dto.response.EventSummaryResponse;
import com.unisphere.backend.commerce.events.enums.EventCategory;
import com.unisphere.backend.commerce.events.enums.EventStatus;
import com.unisphere.backend.commerce.events.service.EventService;
import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Events", description = "Create, browse and manage campus events, including map and geo search")
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @Operation(summary = "Create an event — always starts as DRAFT")
    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
            @Valid @RequestBody CreateEventRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(eventService.createEvent(req, currentUser), "Event created"));
    }

    /**
     * Browse. A null {@code status} defaults to PUBLISHED; an explicit {@code status=COMPLETED} is
     * how a "past events" view is reached — DRAFT is rejected outright, see {@code EventService}.
     */
    @Operation(summary = "Browse events, filtered by category, status, online-ness and organizer")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<EventSummaryResponse>>> getFeed(
            @RequestParam(required = false) EventCategory category,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) Boolean isOnline,
            @RequestParam(required = false) Long organizerId,
            @PageableDefault(size = 20, sort = "startDatetime", direction = Sort.Direction.ASC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                eventService.getFeed(category, status, isOnline, organizerId, pageable, currentUser)));
    }

    @Operation(summary = "Full-text search over event titles and descriptions")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<EventSummaryResponse>>> search(
            @RequestParam String q,
            @RequestParam(required = false) EventCategory category,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.search(q, category, pageable, currentUser)));
    }

    @Operation(summary = "Find events near a coordinate, ordered by distance")
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<Page<EventNearbyResponse>>> getNearby(
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) EventCategory category,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.getNearby(lat, lng, radiusKm, category, pageable, currentUser)));
    }

    @Operation(summary = "Map pins within a bounding-box viewport — PUBLISHED events only")
    @GetMapping("/map")
    public ResponseEntity<ApiResponse<List<EventMapPinResponse>>> getMapPins(
            @RequestParam BigDecimal minLat,
            @RequestParam BigDecimal maxLat,
            @RequestParam BigDecimal minLng,
            @RequestParam BigDecimal maxLng,
            @RequestParam(required = false) EventCategory category,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                eventService.getMapPins(minLat, maxLat, minLng, maxLng, category, currentUser)));
    }

    @Operation(summary = "The caller's own events, every status including DRAFT")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<EventSummaryResponse>>> getMyEvents(
            @RequestParam(required = false) EventStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.getMyEvents(status, pageable, currentUser)));
    }

    @Operation(summary = "Get one event. A DRAFT is masked as 404 unless you're the organizer/ADMIN")
    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventResponse>> getEvent(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.getEventById(eventId, currentUser)));
    }

    @Operation(summary = "Update an event you organize")
    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateEventRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.updateEvent(eventId, req, currentUser), "Event updated"));
    }

    @Operation(summary = "Publish or cancel an event")
    @PatchMapping("/{eventId}/status")
    public ResponseEntity<ApiResponse<EventResponse>> changeStatus(
            @PathVariable Long eventId,
            @Valid @RequestBody EventStatusUpdateRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.changeStatus(eventId, req, currentUser), "Event status updated"));
    }

    @Operation(summary = "Delete an event you organize — blocked while it has active registrations")
    @DeleteMapping("/{eventId}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        eventService.deleteEvent(eventId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Event deleted"));
    }

    @Operation(summary = "Registration breakdown for an event you organize")
    @GetMapping("/{eventId}/stats")
    public ResponseEntity<ApiResponse<EventStatsResponse>> getStats(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.getStats(eventId, currentUser)));
    }
}
