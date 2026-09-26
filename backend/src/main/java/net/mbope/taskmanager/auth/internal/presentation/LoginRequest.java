package net.mbope.taskmanager.auth.internal.presentation;

record LoginRequest(String email, String password) {
    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
