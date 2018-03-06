/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.prometheus.canary;

import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.CanaryScopeFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PrometheusCanaryScopeFactory implements CanaryScopeFactory {

  @Override
  public boolean handles(String serviceType) {
    return "prometheus".equals(serviceType);
  }

  @Override
  public CanaryScope buildCanaryScope(CanaryScope canaryScope){
    PrometheusCanaryScope prometheusCanaryScope = new PrometheusCanaryScope();
    prometheusCanaryScope.setScope(canaryScope.getScope());
    prometheusCanaryScope.setRegion(canaryScope.getRegion());
    prometheusCanaryScope.setStart(canaryScope.getStart());
    prometheusCanaryScope.setEnd(canaryScope.getEnd());
    prometheusCanaryScope.setStep(canaryScope.getStep());
    prometheusCanaryScope.setExtendedScopeParams(canaryScope.getExtendedScopeParams());

    Map<String, String> extendedScopeParams = prometheusCanaryScope.getExtendedScopeParams();

    if (extendedScopeParams != null) {
      if (extendedScopeParams.containsKey("project")) {
        prometheusCanaryScope.setProject(extendedScopeParams.get("project"));
      }

      if (extendedScopeParams.containsKey("resourceType")) {
        prometheusCanaryScope.setResourceType(extendedScopeParams.get("resourceType"));
      }
    }

    return prometheusCanaryScope;
  }
}
