package com.unisphere.backend.commerce.events.controller;

import com.unisphere.backend.commerce.events.dto.request.EventCheckInRequest;
import com.unisphere.backend.commerce.events.dto.request.EventRegistrationStatusUpdateRequest;
import com.unisphere.backend.commerce.events.dto.response.EventRegistrationResponse;
import com.unisphere.backend.commerce.events.enums.EventRegistrationStatus;
import com.unisphere.backend.commerce.events.service.EventRegistrationService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /register}, {@code /check-in} and the item-scoped roster nest under {@code /events/{id}};
 * "my tickets" and single-registration status changes are flat, since a registration id is globally
 * unique — the exact split {@code LostFoundClaimController} draws.
 */
@Tag(name = "Event registrations", description = "Register for events, manage your tickets, and organizer check-in")
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventRegistrationController {

    private final EventRegistrationService eventRegistrationService;

    @Operation(summary = "Register for an event — seats you, or waitlists you if it's full")
    @PostMapping("/{eventId}/register")
    public ResponseEntity<ApiResponse<EventRegistrationResponse>> register(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(eventRegistrationService.register(eventId, currentUser), "Registered"));
    }

    @Operation(summary = "Attendee roster for an event you organize")
    @GetMapping("/{eventId}/registrations")
    public ResponseEntity<ApiResponse<Page<EventRegistrationResponse>>> listRegistrations(
            @PathVariable Long eventId,
            @RequestParam(required = false) EventRegistrationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                eventRegistrationService.listRegistrations(eventId, status, pageable, currentUser)));
    }

    @Operation(summary = "Your own registration for one event")
    @GetMapping("/{eventId}/registrations/me")
    public ResponseEntity<ApiResponse<EventRegistrationResponse>> myRegistrationForEvent(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                eventRegistrationService.myRegistrationForEvent(eventId, currentUser)));
    }

    @Operation(summary = "\"My tickets\" — every registration you hold, across every event")
    @GetMapping("/registrations/me")
    public ResponseEntity<ApiResponse<Page<EventRegistrationResponse>>> myRegistrations(
            @RequestParam(required = false) EventRegistrationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(eventRegistrationService.myRegistrations(status, pageable, currentUser)));
    }

    @Operation(summary = "Cancel a registration — yours, or one on an event you organize")
    @PatchMapping("/registrations/{registrationId}")
    public ResponseEntity<ApiResponse<EventRegistrationResponse>> updateRegistrationStatus(
            @PathVariable Long registrationId,
            @Valid @RequestBody EventRegistrationStatusUpdateRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                eventRegistrationService.updateRegistrationStatus(registrationId, req, currentUser),
                "Registration cancelled"));
    }

    @Operation(summary = "Check a ticket in at the door — organizer/ADMIN only")
    @PostMapping("/{eventId}/check-in")
    public ResponseEntity<ApiResponse<EventRegistrationResponse>> checkIn(
            @PathVariable Long eventId,
            @Valid @RequestBody EventCheckInRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                eventRegistrationService.checkIn(eventId, req, currentUser), "Checked in"));
    }
}
