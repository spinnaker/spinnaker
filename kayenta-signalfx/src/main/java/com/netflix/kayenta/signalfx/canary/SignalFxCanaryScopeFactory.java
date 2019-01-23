/*
 * Copyright (c) 2018 Nike, inc.
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
 *
 */

package com.netflix.kayenta.signalfx.canary;

import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.CanaryScopeFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.netflix.kayenta.canary.providers.metrics.SignalFxCanaryMetricSetQueryConfig.SERVICE_TYPE;

@Component
public class SignalFxCanaryScopeFactory implements CanaryScopeFactory {

  public static String SCOPE_KEY_KEY = "_scope_key";
  public static String LOCATION_KEY_KEY = "_location_key";

  @Override
  public boolean handles(String serviceType) {
    return SERVICE_TYPE.equals(serviceType);
  }

  @Override
  public CanaryScope buildCanaryScope(CanaryScope canaryScope) {

    SignalFxCanaryScope signalFxCanaryScope = new SignalFxCanaryScope();
    signalFxCanaryScope.setScope(canaryScope.getScope());
    signalFxCanaryScope.setLocation(canaryScope.getLocation());
    signalFxCanaryScope.setStart(canaryScope.getStart());
    signalFxCanaryScope.setEnd(canaryScope.getEnd());
    signalFxCanaryScope.setStep(canaryScope.getStep());

    Optional.ofNullable(canaryScope.getExtendedScopeParams()).ifPresent(extendedParameters -> {
      signalFxCanaryScope.setScopeKey(extendedParameters.getOrDefault(SCOPE_KEY_KEY, null));
      signalFxCanaryScope.setLocationKey(extendedParameters.getOrDefault(LOCATION_KEY_KEY, null));
      signalFxCanaryScope.setExtendedScopeParams(extendedParameters);
    });

    return signalFxCanaryScope;
  }
}
