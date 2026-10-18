package com.example.carrental.nl;

public record Issue(IssueKind kind, RequestField field, String message) {

    @Override
    public String toString() {
        return "[%s] %s: %s".formatted(kind, field, message);
    }
}
