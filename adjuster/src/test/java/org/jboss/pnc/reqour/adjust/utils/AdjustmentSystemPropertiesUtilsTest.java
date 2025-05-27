/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class AdjustmentSystemPropertiesUtilsTest {

    @Test
    void getSystemPropertyValue_nameAndValuePresent_returnsValue() {
        String name = "key";
        String value = "value";
        String defaultValue = "default";
        Stream<String> stream = Stream.of("foo", "key=" + value);

        Optional<String> optionalValue = AdjustmentSystemPropertiesUtils
                .getSystemPropertyValue(name, stream, defaultValue);

        assertThat(optionalValue.isPresent()).isTrue();
        assertThat(optionalValue.get()).isEqualTo(value);
    }

    @Test
    void getSystemPropertyValue_namePresentButValueNot_returnsDefaultValue() {
        String name = "key";
        String defaultValue = "default";
        Stream<String> stream = Stream.of("foo", "key");

        Optional<String> optionalValue = AdjustmentSystemPropertiesUtils
                .getSystemPropertyValue(name, stream, defaultValue);

        assertThat(optionalValue.isPresent()).isTrue();
        assertThat(optionalValue.get()).isEqualTo(defaultValue);
    }

    @Test
    void getSystemPropertyValue_nameNotPresent_returnsEmpty() {
        String name = "nonexistent";
        String value = "value";
        String defaultValue = "default";
        Stream<String> stream = Stream.of("foo", "key=" + value);

        Optional<String> optionalValue = AdjustmentSystemPropertiesUtils
                .getSystemPropertyValue(name, stream, defaultValue);

        assertThat(optionalValue.isEmpty()).isTrue();
    }

    @Test
    void getSystemPropertyValue_adjustmentSystemPropertyWithoutValue_returnsDefaultValue() {
        String defaultValue = "true";
        Stream<String> stream = Stream.of("-Dmanipulation.disable");

        Optional<String> optionalValue = AdjustmentSystemPropertiesUtils
                .getSystemPropertyValue(
                        AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.MANIPULATION_DISABLE,
                        stream,
                        defaultValue);

        assertThat(optionalValue.isPresent()).isTrue();
        assertThat(optionalValue.get()).isEqualTo(defaultValue);
    }
}