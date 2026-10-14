/*
 * Copyright 2020 Playtika
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

package com.netflix.kayenta.canary.providers.metrics

import com.netflix.kayenta.canary.CanaryConfig
import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig
import org.springframework.util.StringUtils

class TestCanaryMetricSetQueryConfig implements CanaryMetricSetQueryConfig {

    String template
    String serviceType = "test-service"

    @Override
    CanaryMetricSetQueryConfig cloneWithEscapedTemplate() {
        if (StringUtils.isEmpty(template)) {
            return this
        } else {
            return new TestCanaryMetricSetQueryConfig(template: template.replace('${', '$\\{'))
        }
    }

    @Override
    String getTemplate(CanaryConfig canaryConfig) {
        template
    }

    @Override
    String getServiceType() {
        serviceType
    }
}