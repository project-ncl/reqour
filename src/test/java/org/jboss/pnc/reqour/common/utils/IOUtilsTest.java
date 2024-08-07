/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2024-2024 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.reqour.common.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IOUtilsTest {

    @Test
    void countLinesWithTrailingNewline() {
        assertThat(IOUtils.countLines("line 1\nline 2\n")).isEqualTo(2);
    }

    @Test
    void countLinesWithoutTrailingNewline() {
        assertThat(IOUtils.countLines("line 1\nline 2")).isEqualTo(2);
    }

    @Test
    void splitByNewLineWithTrailingNewline() {
        String one = "one";
        String two = "two";
        String text = one + "\n" + two;

        assertThat(IOUtils.splitByNewLine(text)).isEqualTo(List.of(one, two));
    }

    @Test
    void splitByNewLineWithoutTrailingNewline() {
        String one = "one";
        String two = "two";
        String text = one + "\n" + two;

        assertThat(IOUtils.splitByNewLine(text)).isEqualTo(List.of(one, two));
    }
}