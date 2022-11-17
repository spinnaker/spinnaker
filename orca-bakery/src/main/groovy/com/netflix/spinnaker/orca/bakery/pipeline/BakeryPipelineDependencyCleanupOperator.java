/*
 * Copyright 2022 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.bakery.pipeline;

import com.netflix.spinnaker.orca.bakery.api.BakeryService;
import com.netflix.spinnaker.orca.bakery.api.DeleteBakesRequest;
import com.netflix.spinnaker.orca.notifications.scheduling.PipelineDependencyCleanupOperator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("${bakery-pipeline-dependency-cleanup.enabled:false}")
@RequiredArgsConstructor
public class BakeryPipelineDependencyCleanupOperator implements PipelineDependencyCleanupOperator {

  private final BakeryService bakeryService;

  @Override
  public void cleanup(List<String> pipelineExecutionIds) {
    DeleteBakesRequest deleteBakesRequest = new DeleteBakesRequest();
    deleteBakesRequest.setPipelineExecutionIds(pipelineExecutionIds);

    bakeryService.createDeleteBakesRequest(deleteBakesRequest);
  }
}
