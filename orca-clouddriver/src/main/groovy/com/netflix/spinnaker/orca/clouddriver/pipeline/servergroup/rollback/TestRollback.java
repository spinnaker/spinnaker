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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback;

import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.orca.pipeline.WaitStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

public class TestRollback implements Rollback {
  private Long waitTime;

  @Autowired @JsonIgnore WaitStage waitStage;

  @Override
  public List<Stage> buildStages(Stage parentStage) {
    Map<String, Object> waitContext = Collections.singletonMap("waitTime", waitTime);

    return Collections.singletonList(
        newStage(
            parentStage.getExecution(),
            waitStage.getType(),
            "wait",
            waitContext,
            parentStage,
            SyntheticStageOwner.STAGE_AFTER));
  }

  public void setWaitTime(Long waitTime) {
    this.waitTime = waitTime;
  }
}
