/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * Log events created by this logger *should* be emitted by the following handlers:<br/>
 * - root console handler (in order to be processed by OpenShift)<br/>
 * - kafka handler (in order to be propagated as live logs)<br/>
 * - own file handler ([applicable only to adjuster] content of this file is later sent to bifrost, hence, propagated as
 * final log)<br/>
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER })
public @interface UserLogger {

}
