/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cloudrun;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpsertCloudrunLoadBalancersTask implements CloudProviderAware, RetryableTask {

  public static final String CLOUD_OPERATION_TYPE = "upsertLoadBalancer";
  public static final String CLOUD_PROVIDER = "cloudrun";
  public static final String[] attrList = {"cluster", "target", "credentials", "region"};

  @Override
  public long getBackoffPeriod() {
    return 2000;
  }

  @Override
  public long getTimeout() {
    return 300000;
  }

  private KatoService kato;

  private TargetServerGroupResolver resolver;

  @Autowired
  public UpsertCloudrunLoadBalancersTask(KatoService kato, TargetServerGroupResolver resolver) {
    this.kato = kato;
    this.resolver = resolver;
  }

  @Override
  public TaskResult execute(StageExecution stage) {

    List operations = new ArrayList();
    boolean katoResultExpected = false;
    Map<String, Object> context = new HashMap(stage.getContext());
    List loadBalancerList = (List) context.get("loadBalancers");
    for (Object loadBalancer : loadBalancerList) {
      katoResultExpected = true;
      Map lbcontext = (Map) loadBalancer;
      if (lbcontext.get("splitDescription") != null
          && ((Map) lbcontext.get("splitDescription")).get("allocationDescriptions") != null) {
        List allocationDescList =
            (List) ((Map) lbcontext.get("splitDescription")).get("allocationDescriptions");
        for (Object allocationDesc : allocationDescList) {
          Map description = (Map) allocationDesc;
          String revisionName = resolveTargetServerGroupName(lbcontext, description);
          description.put("serverGroupName", revisionName);
          description.put("revisionName", revisionName);
        }
      }
      Map<String, Object> operation = new HashMap();
      operation.put(CLOUD_OPERATION_TYPE, lbcontext);
      operations.add(operation);
    }
    TaskId taskId = kato.requestOperations(CLOUD_PROVIDER, operations);
    ImmutableMap<String, Object> outputs = getOutputs(operations, taskId, katoResultExpected);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }

  public String resolveTargetServerGroupName(Map loadBalancer, Map allocationDescription) {

    for (String attr : attrList) {
      if (loadBalancer.get(attr) == null && allocationDescription.get(attr) == null) {
        throw new IllegalArgumentException(
            "Could not resolve target server group, " + attr + " not specified.");
      }
    }

    TargetServerGroup.Params params = new TargetServerGroup.Params();
    params.setCloudProvider(CLOUD_PROVIDER);
    params.setCredentials((String) loadBalancer.get("credentials"));
    params.setCluster((String) allocationDescription.get("cluster"));
    params.setTarget(
        TargetServerGroup.Params.Target.valueOf((String) allocationDescription.get("target")));
    Location location = new Location(Location.Type.REGION, (String) loadBalancer.get("region"));
    List<Location> locations = new ArrayList<>();
    locations.add(location);
    params.setLocations(locations);
    List<TargetServerGroup> serverGroups = resolver.resolveByParams(params);
    return (serverGroups != null && !serverGroups.isEmpty()) ? serverGroups.get(0).getName() : "";
  }

  private ImmutableMap<String, Object> getOutputs(
      List<Map<String, Object>> operations, TaskId taskId, boolean katoResultExpected) {

    ImmutableMap.Builder<String, Object> returnMap = new ImmutableMap.Builder<>();
    returnMap.put("notification.type", CLOUD_OPERATION_TYPE.toLowerCase());
    returnMap.put("kato.result.expected", katoResultExpected);
    returnMap.put("kato.last.task.id", taskId);
    Map<String, Object> targetMap = new HashMap<>();
    for (Map<String, Object> op : operations) {
      targetMap = (Map<String, Object>) op.get(CLOUD_OPERATION_TYPE);
    }
    returnMap.put("targets", targetMap);
    return returnMap.build();
  }
}
