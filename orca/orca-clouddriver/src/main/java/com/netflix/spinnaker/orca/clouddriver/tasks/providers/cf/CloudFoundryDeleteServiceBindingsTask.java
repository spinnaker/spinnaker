/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CloudFoundryDeleteServiceBindingsTask extends AbstractCloudFoundryServiceTask {

  private final TargetServerGroupResolver tsgResolver;
  private final ObjectMapper mapper;

  @Autowired
  public CloudFoundryDeleteServiceBindingsTask(
      KatoService kato, TargetServerGroupResolver tsgResolver, ObjectMapper mapper) {
    super(kato);
    this.tsgResolver = tsgResolver;
    this.mapper = mapper;
  }

  @Override
  @Nonnull
  public TaskResult execute(@Nonnull StageExecution stage) {
    List<TargetServerGroup> tsgList = tsgResolver.resolve(stage);
    if (!tsgList.isEmpty()) {
      Optional.ofNullable(tsgList.get(0))
          .ifPresent(
              tsg -> {
                stage.getContext().put("serverGroupName", tsg.getName());
                stage.getContext().put("moniker", tsg.getMoniker());
              });
    }

    List<Map<String, Object>> serviceUnbindingRequests =
        (List<Map<String, Object>>) stage.getContext().get("serviceUnbindingRequests");

    if (serviceUnbindingRequests == null || serviceUnbindingRequests.isEmpty()) {
      throw new IllegalArgumentException(
          "There must be at least 1 or more service binding requests.");
    }

    for (int i = 0; i < serviceUnbindingRequests.size(); i++) {
      mapper.convertValue(serviceUnbindingRequests.get(i), ServiceUnbindingRequest.class);
    }

    stage.getContext().put("serviceUnbindingRequests", serviceUnbindingRequests);

    return super.execute(stage);
  }

  @Override
  protected String getNotificationType() {
    return "deleteServiceBindings";
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ServiceUnbindingRequest {
    private String id;
    private String serviceInstanceName;
  }
}
