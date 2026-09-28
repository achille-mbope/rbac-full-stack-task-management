package net.mbope.taskmanager.user.internal.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

@Schema(name = "SetAccountStatusRequest")
record AccountStatusRequest(@NotNull Boolean enabled) {
}
