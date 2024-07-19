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
package org.jboss.pnc.reqour.profile;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * This test profile extracts common test-profile-related overrides, which would be normally replicated among several
 * test profiles. Hence, test profiles which do not have any unusual requirements should just extend this test profile.
 */
public abstract class CommonTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "test";
    }
}
