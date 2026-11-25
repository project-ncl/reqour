/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import org.eclipse.microprofile.config.spi.Converter;

/**
 * Strips leading and trailing whitespace, including line breaks: a secret written to a file or pasted into a vault
 * often ends with one, which is not a valid HTTP header value.
 */
public class StrippedStringConverter implements Converter<String> {

    @Override
    public String convert(String value) {
        return value == null ? null : value.strip();
    }
}
