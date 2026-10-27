package com.example.healingagent.model;

public record RemediationDecision(Action action, String reason, boolean rollbackSafe) {

    public enum Action { ROLLBACK, NOTIFY_ONLY }

    public static RemediationDecision rollback(String reason) {
        return new RemediationDecision(Action.ROLLBACK, reason, true);
    }

    public static RemediationDecision notifyOnly(String reason) {
        return new RemediationDecision(Action.NOTIFY_ONLY, reason, false);
    }

    @Override
    public String toString() {
        return "RemediationDecision{action=" + action + ", reason='" + reason + "'}";
    }
}
