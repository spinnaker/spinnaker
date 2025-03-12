/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.CloudrunTrafficSplitDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.UpsertCloudrunLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import groovy.util.logging.Slf4j;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class UpsertCloudrunLoadBalancerAtomicOperation extends CloudrunAtomicOperation {

  @Getter private static final String BASE_PHASE = "UPSERT_LOAD_BALANCER";
  @Getter private final UpsertCloudrunLoadBalancerDescription description;
  @Autowired CloudrunJobExecutor jobExecutor;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public UpsertCloudrunLoadBalancerAtomicOperation(
      UpsertCloudrunLoadBalancerDescription description, boolean retryApiCall) {
    this(description);
  }

  public UpsertCloudrunLoadBalancerAtomicOperation(
      UpsertCloudrunLoadBalancerDescription description) {
    this.description = description;
  }

  @Override
  public Map operate(List priorOutputs) {

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing upsert of load balancer "
                + description.getLoadBalancerName()
                + " "
                + "in "
                + description.getRegion()
                + "...");

    String region = description.getRegion();
    String project = description.getCredentials().getProject();
    List<String> deployCommand = new ArrayList<>();
    deployCommand.add("gcloud");
    deployCommand.add("run");
    deployCommand.add("services");
    deployCommand.add("update-traffic");
    deployCommand.add(description.getLoadBalancerName());
    deployCommand.add("--to-revisions=" + appendRevisionNameTrafficSplit(description));
    deployCommand.add("--region=" + region);
    deployCommand.add("--project=" + project);

    String success = "false";
    try {
      jobExecutor.runCommand(deployCommand);
      success = "true";
    } catch (Exception e) {
      throw new CloudrunOperationException(
          "Failed to update traffic for revisions with command "
              + deployCommand
              + "exception "
              + e.getMessage());
    }
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Done upserting "
                + description.getLoadBalancerName()
                + " in "
                + description.getRegion()
                + ".");
    LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, String>>> loadBalancers =
        new LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, String>>>(1);
    LinkedHashMap<String, LinkedHashMap<String, String>> regionMap =
        new LinkedHashMap<String, LinkedHashMap<String, String>>(1);
    LinkedHashMap<String, String> loadBalancerNameMap = new LinkedHashMap<String, String>(1);
    loadBalancerNameMap.put("name", description.getLoadBalancerName());
    regionMap.put(description.getRegion(), loadBalancerNameMap);
    loadBalancers.put("loadBalancers", regionMap);
    return loadBalancers;
  }

  private String appendRevisionNameTrafficSplit(UpsertCloudrunLoadBalancerDescription description) {

    StringBuilder builder = new StringBuilder();
    if (description.getSplitDescription() != null) {
      CloudrunTrafficSplitDescription splitDesc = description.getSplitDescription();
      AtomicInteger counter = new AtomicInteger();
      if (splitDesc != null && !(splitDesc.getAllocationDescriptions().isEmpty())) {
        splitDesc
            .getAllocationDescriptions()
            .forEach(
                trafficSplit -> {
                  builder.append(trafficSplit.getRevisionName());
                  builder.append("=");
                  builder.append(trafficSplit.getPercent());
                  if (!(counter.get() == (splitDesc.getAllocationDescriptions().size() - 1))) {
                    builder.append(",");
                  }
                  counter.getAndIncrement();
                });
      }
    }
    return builder.toString();
  }
}
