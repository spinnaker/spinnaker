/*
 * Copyright 2022 JPMorgan Chase & Co
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

package com.netflix.spinnaker.orca.clouddriver;

import com.netflix.spinnaker.kork.web.selector.SelectableService;
import java.util.List;

/**
 * Wrapper around the {@link FeaturesRestService} which selects an endpoint based on {@link
 * SelectableService.Criteria}.
 */
public class DelegatingFeaturesRestService extends DelegatingClouddriverService<FeaturesRestService>
    implements FeaturesRestService {

  public DelegatingFeaturesRestService(SelectableService selectableService) {
    super(selectableService);
  }

  @Override
  public List<AvailableStage> getStages() {
    return getService().getStages();
  }
}
