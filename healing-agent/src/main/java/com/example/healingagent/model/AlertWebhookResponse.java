package com.example.healingagent.model;

public record AlertWebhookResponse(String incidentId, String status, String message) {
}
