/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jboss.pnc.reqour.common.profile.UtilsProfile;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.TestProfile;

@TestProfile(UtilsProfile.class)
class IOUtilsTest {

    @Test
    void countLinesWithTrailingNewline() {
        assertThat(IOUtils.countLines("line 1" + System.lineSeparator() + "line 2" + System.lineSeparator()))
                .isEqualTo(2);
    }

    @Test
    void countLinesWithoutTrailingNewline() {
        assertThat(IOUtils.countLines("line 1" + System.lineSeparator() + "line 2")).isEqualTo(2);
    }

    @Test
    void splitByNewLineWithTrailingNewline() {
        String one = "one";
        String two = "two";
        String text = one + System.lineSeparator() + two + System.lineSeparator();

        assertThat(IOUtils.splitByNewLine(text)).isEqualTo(List.of(one, two));
    }

    @Test
    void splitByNewLineWithoutTrailingNewline() {
        String one = "one";
        String two = "two";
        String text = one + System.lineSeparator() + two;

        assertThat(IOUtils.splitByNewLine(text)).isEqualTo(List.of(one, two));
    }
}