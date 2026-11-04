package br.com.sentinela.infra.client.snirh;

import java.text.Normalizer;
import java.util.Locale;

public final class SnirhText {

    private SnirhText() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
