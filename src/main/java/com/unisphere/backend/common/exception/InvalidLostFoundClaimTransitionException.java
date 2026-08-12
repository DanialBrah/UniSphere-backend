package com.unisphere.backend.common.exception;

import com.unisphere.backend.campus.lostfound.enums.LostFoundClaimStatus;

public class InvalidLostFoundClaimTransitionException extends RuntimeException {

    public InvalidLostFoundClaimTransitionException(LostFoundClaimStatus from, LostFoundClaimStatus to) {
        super("Cannot move a claim from " + from + " to " + to);
    }
}
