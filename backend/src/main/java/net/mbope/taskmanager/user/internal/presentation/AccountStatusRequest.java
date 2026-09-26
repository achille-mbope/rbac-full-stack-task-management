package net.mbope.taskmanager.user.internal.presentation;

import jakarta.validation.constraints.NotNull;

record AccountStatusRequest(@NotNull Boolean enabled) {
}
