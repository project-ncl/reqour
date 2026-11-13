/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

public class OidcRoleConstants {

    public static final String PNC_APP_REPOUR_USER = "pnc-app-repour-user";
    public static final String PNC_USERS_ADMIN = "pnc-users-admin";
    public static final String PNC_USERS = "pnc-users";
    /** The role orch requires, with {@link #PNC_USERS} and {@link #PNC_USERS_ADMIN}, to read builds. */
    public static final String PNC_APP_ORCH_BUILD_READ = "pnc-app-orch-build-read";
}
