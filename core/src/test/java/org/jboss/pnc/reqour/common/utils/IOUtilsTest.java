/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

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

    @Test
    void unquote_noQuotes_returnsOriginalString() {
        String text = "foo";

        String actual = IOUtils.unquote(text);

        assertThat(actual).isEqualTo(text);
    }

    @Test
    void unquote_withQuotes_returnsUnquotedString() {
        String text = "foo bar";

        String actual = IOUtils.unquote("\"" + text + "\"");

        assertThat(actual).isEqualTo(text);
    }

    @Test
    void quoteIfNeeded() {
        final Function<String, String> str = s -> IOUtils.quoteIfNeeded(new StringBuilder(), s).toString();
        assertThat(str.apply("foo")).isEqualTo("foo");
        assertThat(str.apply("foo\"")).isEqualTo("\"foo\\\"\"");
        assertThat(str.apply("foo bar")).isEqualTo("\"foo bar\"");
        assertThat(str.apply("foo\tbar")).isEqualTo("\"foo\tbar\"");
        assertThat(str.apply("foo\nbar")).isEqualTo("\"foo\nbar\"");
    }
}