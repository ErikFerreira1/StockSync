package com.erikferreira.stocksync.service.exceptions;

import java.util.List;

public class PublicationValidationException extends RuntimeException {
    private final List<String> issues;
    public PublicationValidationException(List<String> issues) {
        super("Publication data was rejected");
        this.issues = List.copyOf(issues);
    }
    public List<String> getIssues() { return issues; }
}
