package com.aircampus.model;

public record Notice(long id, long userId, long createdAt, String message, boolean read) {}
