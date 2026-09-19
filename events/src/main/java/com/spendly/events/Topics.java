package com.spendly.events;

/** Topic names, declared once so producers and consumers cannot drift apart. */
public final class Topics {

    public static final String USER_EVENTS = "spendly.user.events.v1";
    public static final String ACCOUNT_EVENTS = "spendly.account.events.v1";
    public static final String TRANSACTION_EVENTS = "spendly.transaction.events.v1";

    /** Suffix convention for the retry and dead-letter topics built in FI-49. */
    public static String retry(String topic, int attempt) {
        return topic + ".retry-" + attempt;
    }

    public static String deadLetter(String topic) {
        return topic + ".dlq";
    }

    private Topics() {
    }
}
