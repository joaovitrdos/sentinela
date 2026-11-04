package br.com.sentinela.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public class Util implements Serializable {

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNullOrZero(Long vlr) {
        return vlr == null || vlr == 0L;
    }

    public static boolean isNullOrZero(Integer vlr) {
        return vlr == null || vlr == 0;
    }

    public static boolean isNullOrZero(Double vlr) {
        return vlr == null || vlr == 0;
    }

    public static boolean isNullOrEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isEmpty(List list) {
        return list == null || list.isEmpty();
    }

    public static String emptyIfNull(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    public static String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder result = new StringBuilder(text.length());
        boolean startOfWord = true;
        for (int offset = 0; offset < text.length();) {
            int character = text.codePointAt(offset);
            if (Character.isLetter(character)) {
                result.appendCodePoint(startOfWord ? Character.toTitleCase(character) : character);
                startOfWord = false;
            } else {
                result.appendCodePoint(character);
                startOfWord = !Character.isDigit(character);
            }
            offset += Character.charCount(character);
        }
        return result.toString();
    }

}
