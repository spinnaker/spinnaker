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

package com.netflix.kayenta.atlas.canary;

import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.CanaryScopeFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
public class AtlasCanaryScopeFactory implements CanaryScopeFactory {

  @Override
  public boolean handles(String serviceType) {
    return "atlas".equals(serviceType);
  }

  @Override
  public CanaryScope buildCanaryScope(CanaryScope canaryScope){
    AtlasCanaryScope atlasCanaryScope = new AtlasCanaryScope();
    atlasCanaryScope.setScope(canaryScope.getScope());
    atlasCanaryScope.setLocation(canaryScope.getLocation());
    atlasCanaryScope.setStart(canaryScope.getStart());
    atlasCanaryScope.setEnd(canaryScope.getEnd());
    atlasCanaryScope.setStep(canaryScope.getStep());
    atlasCanaryScope.setExtendedScopeParams(canaryScope.getExtendedScopeParams());

    Map<String, String> extendedScopeParams = atlasCanaryScope.getExtendedScopeParams();
    if (extendedScopeParams == null) {
      extendedScopeParams = Collections.emptyMap();
    }

    atlasCanaryScope.setType(extendedScopeParams.getOrDefault("type", "cluster"));
    atlasCanaryScope.setDeployment(extendedScopeParams.getOrDefault("deployment", "main"));
    atlasCanaryScope.setDataset(extendedScopeParams.getOrDefault("dataset", "regional"));
    atlasCanaryScope.setEnvironment(extendedScopeParams.getOrDefault("environment", "test"));
    atlasCanaryScope.setAccountId(extendedScopeParams.get("accountId"));

    return atlasCanaryScope;
  }
}
