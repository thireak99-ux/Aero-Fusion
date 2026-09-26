package com.aircampus.model;

public record AuditEntry(long id, long createdAt, String actor, String action, String details) {}
