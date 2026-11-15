package com.workbench.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLogTest {

    @Test
    void masksEmailKeepingOnlyTheFirstLetterAndTheDomain() {
        assertThat(JsonLog.maskEmail("maria.silva@oficina.com")).isEqualTo("m***@oficina.com");
        assertThat(JsonLog.maskEmail("sem-arroba")).isEqualTo("***");
        assertThat(JsonLog.maskEmail(null)).isEqualTo("***");
    }

    @Test
    void masksDocument() {
        assertThat(JsonLog.maskDocument("52998224725")).isEqualTo("529******25");
    }
}
