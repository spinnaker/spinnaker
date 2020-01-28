/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.gate.config.DynamicRoutingConfigProperties;
import com.netflix.spinnaker.gate.security.RequestContext;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.web.selector.SelectableService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClouddriverServiceSelector {
  private final SelectableService selectableService;
  private final DynamicConfigService dynamicConfigService;

  public ClouddriverServiceSelector(
      SelectableService selectableService, DynamicConfigService dynamicConfigService) {
    this.selectableService = selectableService;
    this.dynamicConfigService = dynamicConfigService;
  }

  /** @deprecated see {@link #withContext(RequestContext)} */
  @Deprecated
  public ClouddriverService select(String key) {
    SelectableService.Criteria criteria = new SelectableService.Criteria();

    if (key != null && shouldSelect()) {
      criteria = criteria.withOrigin(key);
    }

    ClouddriverService selected = (ClouddriverService) selectableService.getService(criteria);
    return selected;
  }

  public ClouddriverService withContext(RequestContext context) {
    SelectableService.Criteria criteria = new SelectableService.Criteria();

    if (context != null && shouldSelect()) {
      criteria =
          criteria
              .withApplication(context.getApplication())
              .withAuthenticatedUser(context.getAuthenticatedUser())
              .withExecutionId(context.getExecutionId())
              .withOrigin(context.getOrigin())
              .withExecutionType(context.getExecutionType());
    }

    return (ClouddriverService) selectableService.getService(criteria);
  }

  private boolean shouldSelect() {
    return dynamicConfigService.isEnabled(DynamicRoutingConfigProperties.ENABLED_PROPERTY, false)
        && dynamicConfigService.isEnabled(
            DynamicRoutingConfigProperties.ClouddriverConfigProperties.ENABLED_PROPERTY, false);
  }
}
