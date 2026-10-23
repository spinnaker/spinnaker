/*
 * Copyright 2026 Ericsson AB
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
package com.netflix.spinnaker.orca.igor.pipeline;

import com.netflix.spinnaker.orca.igor.tasks.StopBuildJobTask;
import org.springframework.stereotype.Component;

/**
 * Stage for triggering and monitoring a GitLab CI pipeline. Reuses the shared CI build task graph
 * (start / wait-for-start / monitor / get-properties / get-artifacts) defined in {@link CIStage}.
 *
 * <p>The stage type resolves to {@code gitlabCi}.
 */
@Component
public class GitlabCiStage extends CIStage {
  public GitlabCiStage(StopBuildJobTask stopBuildJobTask) {
    super(stopBuildJobTask);
  }
}
