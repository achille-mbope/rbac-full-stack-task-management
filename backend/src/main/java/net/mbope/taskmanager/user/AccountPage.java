package net.mbope.taskmanager.user;

import java.util.List;

public record AccountPage(List<Account> items, int page, int size, long totalElements, int totalPages) {
    public AccountPage {
        items = List.copyOf(items);
    }
}
