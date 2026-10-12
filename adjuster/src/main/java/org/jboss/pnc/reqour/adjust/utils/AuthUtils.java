/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for constructing authentication credentials passed to manipulator subprocesses.
 */
public class AuthUtils {

    /**
     * Creates a {@code restHeaders} value in the format expected by PME/GME ({@code key:value} pairs separated by
     * commas). The resulting header uses HTTP Basic authentication with Base64-encoded credentials.
     *
     * @param username LDAP service account username
     * @param password LDAP service account password
     * @return header string in the format {@code Authorization:Basic <base64(username:password)>}
     */
    public static String createBasicAuthRestHeaders(String username, String password) {
        String credentials = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Authorization:Basic " + credentials;
    }
}
