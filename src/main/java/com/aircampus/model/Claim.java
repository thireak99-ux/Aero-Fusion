package com.aircampus.model;

public record Claim(long id, long userId, String reference, String description, String status) {}
