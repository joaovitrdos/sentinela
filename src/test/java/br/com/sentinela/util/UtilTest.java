package br.com.sentinela.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UtilTest {

    @Test
    void blankTextIsTreatedAsMissing() {
        assertThat(Util.isNullOrBlank((String) null)).isTrue();
        assertThat(Util.isNullOrBlank("   ")).isTrue();
        assertThat(Util.isNullOrBlank(" a ")).isFalse();
        assertThat(Util.isNullOrBlank((Object) " ")).isTrue();
        assertThat(Util.isNullOrBlank((Object) 10)).isFalse();
    }

    @Test
    void trimToNullKeepsOnlyRealText() {
        assertThat(Util.trimToNull((String) null)).isNull();
        assertThat(Util.trimToNull("  ")).isNull();
        assertThat(Util.trimToNull("  soja  ")).isEqualTo("soja");
        assertThat(Util.trimToNull((Object) 42)).isEqualTo("42");
    }
}
