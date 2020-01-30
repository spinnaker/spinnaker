/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.job;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MonitorJobTask extends MonitorKatoTask {
  private final JobUtils jobUtils;

  @Autowired
  public MonitorJobTask(
      KatoService katoService,
      Registry registry,
      JobUtils jobUtils,
      DynamicConfigService dynamicConfigService,
      RetrySupport retrySupport) {
    super(katoService, registry, dynamicConfigService, retrySupport);
    this.jobUtils = jobUtils;
  }

  public MonitorJobTask(
      KatoService katoService,
      Registry registry,
      DynamicConfigService dynamicConfigService,
      RetrySupport retrySupport) {
    super(katoService, registry, dynamicConfigService, retrySupport);
    this.jobUtils = null;
  }

  @Override
  public @Nullable TaskResult onTimeout(@Nonnull Stage stage) {
    jobUtils.cancelWait(stage);

    return null;
  }

  @Override
  public void onCancel(@Nonnull Stage stage) {
    jobUtils.cancelWait(stage);
  }
}
