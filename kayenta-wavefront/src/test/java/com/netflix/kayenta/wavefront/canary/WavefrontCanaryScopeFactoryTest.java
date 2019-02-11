/*
 * Copyright 2019 Intuit, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.kayenta.wavefront.canary;


import com.netflix.kayenta.canary.CanaryScope;
import org.junit.Test;

import java.time.Instant;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class WavefrontCanaryScopeFactoryTest {

    private WavefrontCanaryScopeFactory queryBuilder = new WavefrontCanaryScopeFactory();

    @Test
    public void testBuildCanaryScope_WithSecondGranularity() {
        CanaryScope canaryScope = new CanaryScope("scope", "location", Instant.now(), Instant.now(), WavefrontCanaryScopeFactory.SECOND, null);
        CanaryScope generatedCanaryScope = queryBuilder.buildCanaryScope(canaryScope);
        WavefrontCanaryScope wavefrontCanaryScope = (WavefrontCanaryScope) generatedCanaryScope;
        assertThat(wavefrontCanaryScope.getGranularity(), is("s"));
    }

    @Test
    public void testBuildCanaryScope_WithMinuteGranularity() {
        CanaryScope canaryScope = new CanaryScope("scope", "location", Instant.now(), Instant.now(), WavefrontCanaryScopeFactory.MINUTE, null);
        CanaryScope generatedCanaryScope = queryBuilder.buildCanaryScope(canaryScope);
        WavefrontCanaryScope wavefrontCanaryScope = (WavefrontCanaryScope) generatedCanaryScope;
        assertThat(wavefrontCanaryScope.getGranularity(), is("m"));
    }

    @Test
    public void testBuildCanaryScope_WithHourGranularity() {
        CanaryScope canaryScope = new CanaryScope("scope", "location", Instant.now(), Instant.now(), WavefrontCanaryScopeFactory.HOUR, null);
        CanaryScope generatedCanaryScope = queryBuilder.buildCanaryScope(canaryScope);
        WavefrontCanaryScope wavefrontCanaryScope = (WavefrontCanaryScope) generatedCanaryScope;
        assertThat(wavefrontCanaryScope.getGranularity(), is("h"));
    }

    @Test
    public void testBuildCanaryScope_WithDayGranularity() {
        CanaryScope canaryScope = new CanaryScope("scope", "location", Instant.now(), Instant.now(), WavefrontCanaryScopeFactory.DAY, null);
        CanaryScope generatedCanaryScope = queryBuilder.buildCanaryScope(canaryScope);
        WavefrontCanaryScope wavefrontCanaryScope = (WavefrontCanaryScope) generatedCanaryScope;
        assertThat(wavefrontCanaryScope.getGranularity(), is("d"));
    }
}
