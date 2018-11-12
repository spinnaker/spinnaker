/*
 * Copyright 2018 Adobe
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

package com.netflix.kayenta.newrelic.canary;

import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.CanaryScopeFactory;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class NewRelicCanaryScopeFactory implements CanaryScopeFactory {
  private final static String SCOPE_KEY_KEY = "_scope_key";

  @Override
  public boolean handles(String serviceType) {
    return "newrelic".equals(serviceType);
  }

  @Override
  public CanaryScope buildCanaryScope(CanaryScope canaryScope) {
    Map<String, String> extendedParameters = Optional.ofNullable(canaryScope.getExtendedScopeParams())
      .orElseThrow(() -> new IllegalArgumentException("New Relic requires extended parameters"));

    NewRelicCanaryScope newRelicCanaryScope = new NewRelicCanaryScope();
    newRelicCanaryScope.setScope(canaryScope.getScope());
    newRelicCanaryScope.setStart(canaryScope.getStart());
    newRelicCanaryScope.setEnd(canaryScope.getEnd());
    newRelicCanaryScope.setStep(canaryScope.getStep());
    newRelicCanaryScope.setExtendedScopeParams(extendedParameters);
    newRelicCanaryScope.setScopeKey(getRequiredExtendedParam(SCOPE_KEY_KEY, extendedParameters));

    return newRelicCanaryScope;
  }

  private String getRequiredExtendedParam(String key, Map<String, String> extendedParameters) {
    if (!extendedParameters.containsKey(key)) {
      throw new IllegalArgumentException(
        String.format("New Relic requires that %s is set in the extended scope params", key));
    }
    return extendedParameters.get(key);
  }
}
