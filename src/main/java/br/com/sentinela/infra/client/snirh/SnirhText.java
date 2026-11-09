package br.com.sentinela.infra.client.snirh;

import br.com.sentinela.util.Util;

import java.text.Normalizer;
import java.util.Locale;

public final class SnirhText {

    private SnirhText() {
    }

    public static String normalize(String text) {
        if (Util.isNull(text)) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
