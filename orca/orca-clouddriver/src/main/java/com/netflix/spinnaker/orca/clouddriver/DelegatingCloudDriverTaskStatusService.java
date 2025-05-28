/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver;

import com.netflix.spinnaker.kork.web.selector.SelectableService;
import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.model.TaskOwner;
import javax.annotation.Nonnull;
import retrofit2.Call;

public class DelegatingCloudDriverTaskStatusService
    extends DelegatingClouddriverService<CloudDriverTaskStatusService>
    implements CloudDriverTaskStatusService {

  public DelegatingCloudDriverTaskStatusService(SelectableService selectableService) {
    super(selectableService);
  }

  @Override
  public Call<Task> lookupTask(String id) {
    return getService().lookupTask(id);
  }

  @Override
  public Call<TaskOwner> lookupTaskOwner(@Nonnull String cloudProvider, String id) {
    return getService().lookupTaskOwner(cloudProvider, id);
  }
}
