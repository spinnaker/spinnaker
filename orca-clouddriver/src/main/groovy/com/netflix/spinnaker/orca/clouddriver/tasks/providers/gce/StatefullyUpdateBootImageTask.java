/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.gce;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.gce.StatefullyUpdateBootImageStage.StageData;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.Observable;

@Component
public final class StatefullyUpdateBootImageTask extends AbstractCloudProviderAwareTask {

  private static final String KATO_OP_NAME = "statefullyUpdateBootImage";

  private final KatoService katoService;
  private final TargetServerGroupResolver resolver;

  @Autowired
  StatefullyUpdateBootImageTask(KatoService katoService, TargetServerGroupResolver resolver) {
    this.katoService = katoService;
    this.resolver = resolver;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    StageData data = stage.mapTo(StageData.class);

    List<TargetServerGroup> resolvedServerGroups = resolver.resolve(stage);
    checkArgument(
        resolvedServerGroups.size() > 0,
        "Could not find a server group named %s for %s in %s",
        data.serverGroupName,
        data.accountName,
        data.region);
    checkState(
        resolvedServerGroups.size() == 1,
        "Found multiple server groups named %s for %s in %s",
        data.serverGroupName,
        data.accountName,
        data.region);
    TargetServerGroup serverGroup = resolvedServerGroups.get(0);

    ImmutableMap<String, String> opData =
        ImmutableMap.of(
            "credentials", getCredentials(stage),
            "serverGroupName", serverGroup.getName(),
            "region", data.getRegion(),
            "bootImage", data.bootImage);

    Map<String, Map> operation = ImmutableMap.of(KATO_OP_NAME, opData);
    Observable<TaskId> observable =
        katoService.requestOperations(getCloudProvider(stage), ImmutableList.of(operation));
    observable.toBlocking().first();

    ImmutableMap<String, ImmutableList<String>> modifiedServerGroups =
        ImmutableMap.of(data.getRegion(), ImmutableList.of(serverGroup.getName()));
    ImmutableMap<String, Object> context =
        ImmutableMap.of(
            "notification.type", KATO_OP_NAME.toLowerCase(),
            "serverGroupName", serverGroup.getName(),
            "deploy.server.groups", modifiedServerGroups);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
  }
}
