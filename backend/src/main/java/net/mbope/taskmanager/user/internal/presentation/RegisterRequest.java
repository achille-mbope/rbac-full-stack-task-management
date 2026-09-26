package net.mbope.taskmanager.user.internal.presentation;

record RegisterRequest(String email, String password) {
    @Override
    public String toString() {
        return "RegisterRequest[redacted]";
    }
}
