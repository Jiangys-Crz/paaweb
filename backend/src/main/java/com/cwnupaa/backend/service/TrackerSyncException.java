package com.cwnupaa.backend.service;

/** A tracker failure with a message safe to include in a user notification. */
final class TrackerSyncException extends IllegalStateException {
    TrackerSyncException(String message) {
        super(message);
    }
}
