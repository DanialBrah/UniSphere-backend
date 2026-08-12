package com.unisphere.backend.common.exception;

import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;

public class InvalidLostFoundStatusTransitionException extends RuntimeException {

    public InvalidLostFoundStatusTransitionException(LostFoundItemStatus from, LostFoundItemStatus to) {
        super("Cannot move a lost & found item from " + from + " to " + to);
    }

    public InvalidLostFoundStatusTransitionException(String message) {
        super(message);
    }
}
