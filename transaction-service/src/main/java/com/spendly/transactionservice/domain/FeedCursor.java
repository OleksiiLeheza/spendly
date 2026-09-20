package com.spendly.transactionservice.domain;

import com.spendly.common.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

public record FeedCursor(LocalDate bookedOn, UUID id) {

    public String encode() {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((bookedOn + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    public static FeedCursor decode(String cursor) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 2);
            return new FeedCursor(LocalDate.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException ex) {
            throw new ApiException.Unprocessable("The cursor is not one this feed issued");
        }
    }
}
