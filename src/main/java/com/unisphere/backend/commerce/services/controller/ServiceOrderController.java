package com.unisphere.backend.commerce.services.controller;

import com.unisphere.backend.commerce.services.dto.request.CreateServiceOrderRequest;
import com.unisphere.backend.commerce.services.dto.request.ServiceOrderStatusUpdateRequest;
import com.unisphere.backend.commerce.services.dto.response.ServiceOrderResponse;
import com.unisphere.backend.commerce.services.enums.ServiceOrderStatus;
import com.unisphere.backend.commerce.services.service.ServiceOrderService;
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
 * {@code /orders} (place order) and the listing-scoped roster nest under {@code /services/{id}};
 * "my orders"/"received orders" and single-order status changes are flat, since an order id is
 * globally unique — the exact split {@code JobApplicationController} draws.
 */
@Tag(name = "Service orders", description = "Request a service, track the order lifecycle, and manage requests you've received")
@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceOrderController {

    private final ServiceOrderService serviceOrderService;

    @Operation(summary = "Request a service — auto-opens/reuses a DM with the provider and posts a SYSTEM summary message")
    @PostMapping("/{listingId}/orders")
    public ResponseEntity<ApiResponse<ServiceOrderResponse>> createOrder(
            @PathVariable Long listingId,
            @Valid @RequestBody CreateServiceOrderRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(serviceOrderService.createOrder(listingId, req, currentUser), "Service order placed"));
    }

    @Operation(summary = "Order roster for a listing you provide")
    @GetMapping("/{listingId}/orders")
    public ResponseEntity<ApiResponse<Page<ServiceOrderResponse>>> listOrdersForListing(
            @PathVariable Long listingId,
            @RequestParam(required = false) ServiceOrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                serviceOrderService.listOrdersForListing(listingId, status, pageable, currentUser)));
    }

    @Operation(summary = "\"My orders\" — every order you've placed as a client, across every listing")
    @GetMapping("/orders/me")
    public ResponseEntity<ApiResponse<Page<ServiceOrderResponse>>> myOrders(
            @RequestParam(required = false) ServiceOrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(serviceOrderService.myOrders(status, pageable, currentUser)));
    }

    @Operation(summary = "\"Orders received\" — every order placed against a listing you provide, across every listing you own")
    @GetMapping("/orders/received")
    public ResponseEntity<ApiResponse<Page<ServiceOrderResponse>>> receivedOrders(
            @RequestParam(required = false) ServiceOrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(serviceOrderService.receivedOrders(status, pageable, currentUser)));
    }

    @Operation(summary = "Get one order — visible to its client, its listing's provider, or an admin")
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<ServiceOrderResponse>> getOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(serviceOrderService.getOrderById(orderId, currentUser)));
    }

    @Operation(summary = "Update an order's status — provider accept/start/complete, either party cancel/dispute")
    @PatchMapping("/orders/{orderId}/status")
    public ResponseEntity<ApiResponse<ServiceOrderResponse>> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody ServiceOrderStatusUpdateRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                serviceOrderService.updateOrderStatus(orderId, req, currentUser), "Order status updated"));
    }
}
