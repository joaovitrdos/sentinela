package br.com.sentinela.util;

import java.util.Locale;

public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return Util.isNull(email) ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
