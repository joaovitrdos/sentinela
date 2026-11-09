package br.com.sentinela.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Util implements Serializable {

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /** Nulo, vazio ou só espaços. */
    public static boolean isNullOrBlank(String str) {
        return str == null || str.isBlank();
    }

    /** Para valores vindos de JSON/filtros, em que o texto pode chegar como qualquer tipo. */
    public static boolean isNullOrBlank(Object obj) {
        return obj == null || obj.toString().isBlank();
    }

    /** Texto sem espaços nas pontas, ou {@code null} quando não sobra nada. */
    public static String trimToNull(String str) {
        return isNullOrBlank(str) ? null : str.trim();
    }

    public static String trimToNull(Object obj) {
        return isNullOrBlank(obj) ? null : obj.toString().trim();
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

    public static boolean isNullOrEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    public static boolean isEmpty(List list) {
        return list == null || list.isEmpty();
    }

    public static String emptyIfNull(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    public static boolean anyNull(Object... objs) {
        for (Object obj : objs) {
            if (obj == null) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNull(Object obj) {
        return obj == null;
    }

    public static boolean nonNull(Object obj) {
        return obj != null;
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
