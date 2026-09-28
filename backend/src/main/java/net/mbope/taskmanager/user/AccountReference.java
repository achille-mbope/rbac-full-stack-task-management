package net.mbope.taskmanager.user;

import java.util.UUID;

/**
 * Minimal account data needed to validate task assignment.
 */
public record AccountReference(UUID id, boolean enabled) {
}
