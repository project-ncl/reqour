/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import java.util.Optional;

/**
 * Dedicated forward proxy used to reach <em>protected</em> external source-code providers when cloning
 * their origin (e.g. a firewalled environment that can only egress to GitLab via an authenticated
 * proxy). It is applied only to the origin clone of a matched
 * {@link ProtectedSourceCodeGitProviderConfig} — never to the push to the internal mirror repository
 * or to reqour's other (in-cluster) calls.
 * <p>
 * If this proxy performs TLS interception, trust its CA by baking it into the reqour image trust store
 * (there is intentionally no CA option here).
 */
public interface SourceCloneProxyConfig {

    /** Proxy base URL, e.g. {@code http://proxy-host:port} (no credentials here). */
    String url();

    /** Proxy username, if the proxy requires authentication. */
    Optional<String> username();

    /** Proxy password/token, if the proxy requires authentication. Supply via a secret. */
    Optional<String> password();
}
