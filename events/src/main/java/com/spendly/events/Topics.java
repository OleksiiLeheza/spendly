package com.spendly.events;

/** Topic names, declared once so producers and consumers cannot drift apart. */
public final class Topics {

    public static final String USER_EVENTS = "spendly.user.events.v1";
    public static final String ACCOUNT_EVENTS = "spendly.account.events.v1";
    public static final String TRANSACTION_EVENTS = "spendly.transaction.events.v1";
    public static final String BUDGET_EVENTS = "spendly.budget.events.v1";
    public static final String RECURRING_EVENTS = "spendly.recurring.events.v1";
    public static final String IMPORT_EVENTS = "spendly.import.events.v1";
    public static final String GOAL_EVENTS = "spendly.goal.events.v1";
    public static final String ANALYTICS_EVENTS = "spendly.analytics.events.v1";
    public static final String SHARING_EVENTS = "spendly.sharing.events.v1";

    /**
     * Retry topics are per consumer service, because several services consume one topic and must not retry each other's
     * failures (FI-49).
     */
    public static String retry(String topic, String service, int index) {
        return topic + "." + service + ".retry-" + index;
    }

    public static String deadLetter(String topic, String service) {
        return topic + "." + service + ".dlq";
    }

    private Topics() {
    }
}
