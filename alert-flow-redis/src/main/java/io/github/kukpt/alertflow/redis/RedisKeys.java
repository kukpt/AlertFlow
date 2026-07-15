package io.github.kukpt.alertflow.redis;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class RedisKeys {
    private RedisKeys() {}

    static String encode(String value) {
        if (value == null) return "";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static String decode(String value) {
        if (value == null || value.isEmpty()) return null;
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
