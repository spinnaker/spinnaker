/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.fiat.shared

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import spock.lang.Specification

abstract class FiatSharedSpecification extends Specification {
    FiatService fiatService = Mock(FiatService)
    Registry registry = new NoopRegistry()
    FiatStatus fiatStatus = Mock(FiatStatus) {
        _ * isEnabled() >> { return true }
        _ * isGrantedAuthoritiesEnabled() >> { return true }
    }

    private static FiatClientConfigurationProperties buildConfigurationProperties() {
        FiatClientConfigurationProperties configurationProperties = new FiatClientConfigurationProperties()
        configurationProperties.enabled = true
        configurationProperties.cache.maxEntries = 0
        configurationProperties.cache.expiresAfterWriteSeconds = 0
        return configurationProperties
    }
}
