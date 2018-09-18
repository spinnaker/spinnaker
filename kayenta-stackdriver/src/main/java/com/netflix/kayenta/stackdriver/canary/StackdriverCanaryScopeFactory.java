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

package com.netflix.kayenta.stackdriver.canary;

import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.CanaryScopeFactory;
import com.netflix.kayenta.canary.providers.metrics.StackdriverCanaryMetricSetQueryConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StackdriverCanaryScopeFactory implements CanaryScopeFactory {

  @Override
  public boolean handles(String serviceType) {
    return StackdriverCanaryMetricSetQueryConfig.SERVICE_TYPE.equals(serviceType);
  }

  @Override
  public CanaryScope buildCanaryScope(CanaryScope canaryScope){
    StackdriverCanaryScope stackdriverCanaryScope = new StackdriverCanaryScope();
    stackdriverCanaryScope.setScope(canaryScope.getScope());
    stackdriverCanaryScope.setLocation(canaryScope.getLocation());
    stackdriverCanaryScope.setStart(canaryScope.getStart());
    stackdriverCanaryScope.setEnd(canaryScope.getEnd());
    stackdriverCanaryScope.setStep(canaryScope.getStep());
    stackdriverCanaryScope.setExtendedScopeParams(canaryScope.getExtendedScopeParams());

    Map<String, String> extendedScopeParams = stackdriverCanaryScope.getExtendedScopeParams();

    if (extendedScopeParams != null) {
      if (extendedScopeParams.containsKey("project")) {
        stackdriverCanaryScope.setProject(extendedScopeParams.get("project"));
      }

      if (extendedScopeParams.containsKey("resourceType")) {
        stackdriverCanaryScope.setResourceType(extendedScopeParams.get("resourceType"));
      }

      if (extendedScopeParams.containsKey("crossSeriesReducer")) {
        stackdriverCanaryScope.setCrossSeriesReducer(extendedScopeParams.get("crossSeriesReducer"));
      }

      if (extendedScopeParams.containsKey("perSeriesAligner")) {
        stackdriverCanaryScope.setPerSeriesAligner(extendedScopeParams.get("perSeriesAligner"));
      }
    }

    return stackdriverCanaryScope;
  }
}
