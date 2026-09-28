package net.mbope.taskmanager.user.internal.presentation;

record ChangePasswordRequest(String currentPassword, String newPassword) {
    @Override
    public String toString() {
        return "ChangePasswordRequest[redacted]";
    }
}
