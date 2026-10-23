/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Explicitly continues the exact native ECS service deployment from the preceding deploy stage. */
@Component
public class EcsNativeContinueServiceDeploymentTask
    extends EcsNativeServiceDeploymentLifecycleTask {

  static final String OPERATION = "continueServiceDeployment";

  @Override
  public String getServerGroupAction() {
    return OPERATION;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Map<String, Object> getAdditionalContext(StageExecution stage, Map operation) {
    // Rejecting the paused deployment is a requested outcome, so its rollback is not a failure.
    Object lifecycleAction = stage.getContext().get("lifecycleAction");
    boolean rollback =
        lifecycleAction != null
            && "ROLLBACK".equals(lifecycleAction.toString().toUpperCase(Locale.ROOT));
    return rollback ? Map.of(ACCEPT_ROLLBACK, true) : Map.of();
  }
}
