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

import com.netflix.spinnaker.gate.security.RequestContext;
import com.netflix.spinnaker.kork.web.selector.SelectableService;
public class OrcaServiceSelector {

  private final SelectableService selectableService;

  public OrcaServiceSelector(SelectableService selectableService) {
    this.selectableService = selectableService;
  }

  public OrcaService withContext(RequestContext context) {
    SelectableService.Criteria criteria = new SelectableService.Criteria(null, null, null, null, null);

    if (context != null) {
      criteria = new SelectableService.Criteria(
        context.getApplication(),
        context.getAuthenticatedUser(),
        context.getExecutionType(),
        context.getExecutionId(),
        context.getOrigin()
      );
    }

    return (OrcaService) selectableService.getService(criteria);
  }
}
