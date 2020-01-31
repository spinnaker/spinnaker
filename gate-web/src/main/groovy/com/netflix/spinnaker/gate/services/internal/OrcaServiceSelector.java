/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.kork.web.context.RequestContext;
import com.netflix.spinnaker.kork.web.context.RequestContextProvider;
import com.netflix.spinnaker.kork.web.selector.SelectableService;

public class OrcaServiceSelector {
  private final SelectableService<OrcaService> selectableService;
  private final RequestContextProvider contextProvider;

  public OrcaServiceSelector(
      SelectableService<OrcaService> selectableService, RequestContextProvider contextProvider) {
    this.selectableService = selectableService;
    this.contextProvider = contextProvider;
  }

  public OrcaService select() {
    SelectableService.Criteria criteria = new SelectableService.Criteria();
    RequestContext context = contextProvider.get();
    if (context != null) {
      criteria =
          criteria
              .withApplication(context.getApplication().orElse(null))
              .withAuthenticatedUser(context.getUser().orElse(null))
              .withExecutionId(context.getExecutionId().orElse(null))
              .withOrigin(context.getUserOrigin().orElse(null))
              .withExecutionType(context.getExecutionType().orElse(null));
    }
    return selectableService.getService(criteria);
  }
}
