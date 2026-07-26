package com.unisphere.backend.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

final class ClientIpResolver {

    private ClientIpResolver() {
    }

    static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // "client, proxy1, proxy2, ...": each hop only ever APPENDS what it observed, so the
            // LAST entry is the address our own (trusted) reverse proxy saw. The client can stuff
            // anything into the earlier entries, but not that one. Assumes exactly one trusted
            // proxy hop in front of the app (true for a single load balancer / PaaS edge).
            String[] hops = forwardedFor.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
