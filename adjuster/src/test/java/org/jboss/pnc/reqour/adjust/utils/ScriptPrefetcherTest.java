/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.junit.jupiter.api.Test;

class ScriptPrefetcherTest {

    @Test
    void convertToApiUri_standardRawUrl_convertsCorrectly() throws Exception {
        URI input = new URI(
                "https://github.ibm.com/pnc-prod/micrometer-metrics-micrometer/raw/1.15.12.redhat-1/groovy/productize.groovy");

        URI result = ScriptPrefetcher.convertToApiUri(input);

        assertThat(result.getScheme()).isEqualTo("https");
        assertThat(result.getHost()).isEqualTo("github.ibm.com");
        assertThat(result.getPath())
                .isEqualTo("/api/v3/repos/pnc-prod/micrometer-metrics-micrometer/contents/groovy/productize.groovy");
        assertThat(result.getQuery()).isEqualTo("ref=1.15.12.redhat-1");
    }

    @Test
    void convertToApiUri_nestedFilePath_convertsCorrectly() throws Exception {
        URI input = new URI("https://github.ibm.com/org/repo/raw/main/src/scripts/align.groovy");

        URI result = ScriptPrefetcher.convertToApiUri(input);

        assertThat(result.getPath()).isEqualTo("/api/v3/repos/org/repo/contents/src/scripts/align.groovy");
        assertThat(result.getQuery()).isEqualTo("ref=main");
    }

    @Test
    void convertToApiUri_noRawSegment_throwsException() throws Exception {
        URI input = new URI("https://github.ibm.com/org/repo/blob/main/file.groovy");

        assertThatThrownBy(() -> ScriptPrefetcher.convertToApiUri(input)).isInstanceOf(AdjusterException.class)
                .hasMessageContaining("does not match expected format");
    }

    @Test
    void convertToApiUri_rawButTooFewSegments_throwsException() throws Exception {
        URI input = new URI("https://github.ibm.com/raw/ref");

        assertThatThrownBy(() -> ScriptPrefetcher.convertToApiUri(input)).isInstanceOf(AdjusterException.class);
    }
}
