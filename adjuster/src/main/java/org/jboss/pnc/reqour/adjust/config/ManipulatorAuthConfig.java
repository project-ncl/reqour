/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config;

/**
 * Configuration for the LDAP service account used by manipulators to authenticate against the Dependency Analyzer REST
 * API.
 */
public interface ManipulatorAuthConfig {

    String username();

    String password();
}
