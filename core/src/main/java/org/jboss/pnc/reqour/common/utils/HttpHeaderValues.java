/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.utils;

import java.net.URI;
import java.net.http.HttpRequest;

/** Whether a value can be sent as an HTTP header value by the JDK's HTTP client. */
public final class HttpHeaderValues {

    /** For log messages about an invalid value; never log the value itself. */
    public static final String INVALID_HINT = "check it for control characters such as line breaks, or characters"
            + " outside ISO-8859-1";

    private HttpHeaderValues() {
    }

    /** No control characters (such as line breaks), only ISO-8859-1 characters: what the JDK accepts. */
    public static boolean valid(String value) {
        try {
            HttpRequest.newBuilder(URI.create("http://localhost/")).header("X-Check", value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
