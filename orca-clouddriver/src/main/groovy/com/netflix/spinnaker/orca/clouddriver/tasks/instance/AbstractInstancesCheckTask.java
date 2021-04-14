/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper;
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper;
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler;
import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import retrofit.RetrofitError;
import retrofit.client.Response;

@Slf4j
public abstract class AbstractInstancesCheckTask
    implements CloudProviderAware, OverridableTimeoutRetryableTask {
  private static final long BACKOFF_PERIOD = TimeUnit.SECONDS.toMillis(10);
  private static final long TIMEOUT = TimeUnit.HOURS.toMillis(2);
  private static final long SERVER_GROUP_WAIT_MINUTES = 10;
  private static final long SERVER_GROUP_WAIT_TIME =
      TimeUnit.MINUTES.toMillis(SERVER_GROUP_WAIT_MINUTES);
  private static final long ONE_MINUTE = TimeUnit.MINUTES.toMillis(1);
  private static final long TWO_MINUTES = TimeUnit.MINUTES.toMillis(2);
  private static final long THIRTY_MINUTES = TimeUnit.MINUTES.toMillis(30);
  private static final long SIXTY_MINUTES = TimeUnit.MINUTES.toMillis(60);

  private static final RetrofitExceptionHandler retrofitExceptionHandler =
      new RetrofitExceptionHandler();

  @Override
  public long getBackoffPeriod() {
    return BACKOFF_PERIOD;
  }

  @Override
  public long getTimeout() {
    return TIMEOUT;
  }

  @Autowired protected CloudDriverService cloudDriverService;

  @Autowired protected ServerGroupCacheForceRefreshTask serverGroupCacheForceRefreshTask;

  @Autowired protected OortHelper oortHelper;

  /**
   * Other components (namely: deck) require this map to be region --> serverGroups, rather than
   * location --> serverGroups, regardless of cloud provider.
   *
   * @return A map of region --> list of serverGroup names.
   */
  protected abstract Map<String, List<String>> getServerGroups(StageExecution stage);

  protected abstract boolean hasSucceeded(
      StageExecution stage,
      Map serverGroup,
      List<Map> instances,
      Collection<String> interestingHealthProviderNames);

  protected Map<String, Object> getAdditionalRunningStageContext(
      StageExecution stage, Map<String, Object> serverGroup) {
    return new HashMap<>();
  }

  // When waiting for up instances during a "Deploy" stage, it is OK for the server group to not
  // exist coming into this
  // task. Instead of failing on a missing server group, we retry the stage until it either
  // succeeds, fails, or times out.
  // For the other uses of this task, we will fail if the server group doesn't exist.
  public boolean waitForUpServerGroup() {
    return false;
  }

  @Override
  public long getDynamicBackoffPeriod(StageExecution stage, Duration taskDuration) {
    if (taskDuration.toMillis() > SIXTY_MINUTES) {
      // task has been running > 60min, drop retry interval to every 2 minutes
      return Math.max(BACKOFF_PERIOD, TWO_MINUTES);
    } else if (taskDuration.toMillis() > THIRTY_MINUTES) {
      // task has been running > 30min, drop retry interval to every 60s
      return Math.max(BACKOFF_PERIOD, ONE_MINUTE);
    }
    return BACKOFF_PERIOD;
  }

  @Override
  public TaskResult execute(StageExecution stage) {
    String account = getCredentials(stage);
    Map<String, List<String>> serverGroupsByRegion = getServerGroups(stage);

    if (serverGroupsByRegion == null
        || serverGroupsByRegion.values().stream().allMatch(List::isEmpty)) {
      String executionId =
          Optional.ofNullable(stage.getExecution()).map(PipelineExecution::getId).orElse(null);
      log.warn(
          String.format(
              "No server groups found in stage context (stage.id: %s, execution.id: %s)",
              stage.getId(), executionId));
      return TaskResult.TERMINAL;
    }
    try {
      String cloudProvider = getCloudProvider(stage);
      Moniker moniker = MonikerHelper.monikerFromStage(stage);
      List<Map<String, Object>> serverGroups =
          fetchServerGroups(
              account, cloudProvider, serverGroupsByRegion, moniker, waitForUpServerGroup());
      if (serverGroups.isEmpty()) {
        return TaskResult.RUNNING;
      }

      Map<String, Object> context = stage.getContext();

      Map<String, Boolean> seenServerGroup = new HashMap<>();
      serverGroupsByRegion.values().stream()
          .flatMap(Collection::stream)
          .forEach(serverGroup -> seenServerGroup.put(serverGroup, false));

      for (Map<String, Object> serverGroup : serverGroups) {
        String region = (String) serverGroup.get("region");
        String name = (String) serverGroup.get("name");

        boolean matches =
            serverGroupsByRegion.entrySet().stream()
                .anyMatch(
                    entry -> {
                      String sgRegion = entry.getKey();
                      List<String> sgName = entry.getValue();
                      return region.equals(sgRegion) && sgName.contains(name);
                    });

        if (!matches) {
          continue;
        }

        seenServerGroup.put(name, true);

        Collection<String> interestingHealthProviderNames =
            (Collection<String>) context.get("interestingHealthProviderNames");

        List<Map> instances = (List<Map>) serverGroup.get("instances");
        if (instances == null) {
          instances = List.of();
        }

        boolean isComplete =
            hasSucceeded(stage, serverGroup, instances, interestingHealthProviderNames);

        if (!isComplete) {
          Map<String, Object> newContext = getAdditionalRunningStageContext(stage, serverGroup);
          if (!seenServerGroup.isEmpty()) {

            Map<String, Integer> capacity = (Map<String, Integer>) serverGroup.get("capacity");

            if (!context.containsKey("capacitySnapshot")) {
              newContext.put("zeroDesiredCapacityCount", 0);
              newContext.computeIfAbsent(
                  "capacitySnapshot",
                  ignore -> {
                    Map<String, Integer> result = new HashMap<>();
                    result.put("minSize", capacity.get("min"));
                    result.put("desiredCapacity", capacity.get("desired"));
                    result.put("maxSize", capacity.get("max"));
                    return result;
                  });
            }

            if (Objects.equals(capacity.get("desired"), 0)) {
              int zeroDesiredCapacityCount =
                  (Integer) context.getOrDefault("zeroDesiredCapacityCount", 0);
              newContext.put("zeroDesiredCapacityCount", zeroDesiredCapacityCount + 1);
            } else {
              newContext.put("zeroDesiredCapacityCount", 0);
            }
          }
          newContext.put("currentInstanceCount", instances.size());
          return TaskResult.builder(ExecutionStatus.RUNNING).context(newContext).build();
        }
      }

      try {
        verifyServerGroupsExist(stage);
      } catch (MissingServerGroupException e) {
        if (waitForUpServerGroup()) {
          long now = System.currentTimeMillis();
          TaskExecution runningTask =
              stage.getTasks().stream()
                  .filter(task -> task.getStatus() == ExecutionStatus.RUNNING)
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Unable to find currently running task. This is likely a problem with Spinnaker itself."));

          if ((now - runningTask.getStartTime()) > SERVER_GROUP_WAIT_TIME) {
            log.info(
                "Waited over {} minutes for the server group to appear.",
                SERVER_GROUP_WAIT_MINUTES);
            throw e;
          }
          log.info("Waiting for server group to show up, ignoring error: {}", e.getMessage());
          return TaskResult.RUNNING;
        } else {
          throw e;
        }
      }

      if (seenServerGroup.containsValue(false)) {
        return TaskResult.RUNNING;
      } else {
        return TaskResult.SUCCEEDED;
      }
    } catch (RetrofitError e) {
      Response response = e.getResponse();
      if (response != null) {
        if (response.getStatus() == 404) {
          return TaskResult.RUNNING;
        } else if (response.getStatus() >= 500) {
          ExceptionHandler.Response retrofitErrorResponse =
              retrofitExceptionHandler.handle(stage.getName(), e);
          log.error("Unexpected retrofit error ({})", retrofitErrorResponse);
          return TaskResult.builder(ExecutionStatus.RUNNING)
              .context(Map.of("lastRetrofitException", retrofitErrorResponse))
              .build();
        }
      }
      throw e;
    }
  }

  /**
   * Assert that the server groups being acted upon still exist.
   *
   * <p>Will raise an exception in the event that a server group is being modified and is destroyed
   * by an external process.
   */
  protected void verifyServerGroupsExist(StageExecution stage) {
    TaskResult forceCacheRefreshResult = serverGroupCacheForceRefreshTask.execute(stage);

    Map<String, List<String>> serverGroups = getServerGroups(stage);
    String account = getCredentials(stage);
    String cloudProvider = getCloudProvider(stage);

    serverGroups.forEach(
        (String region, List<String> serverGroupNames) ->
            serverGroupNames.forEach(
                it -> {
                  if (!oortHelper
                      .getTargetServerGroup(account, it, region, cloudProvider)
                      .isPresent()) {
                    log.error(
                        "Server group '{}:{}' does not exist (forceCacheRefreshResult: {}",
                        region,
                        it,
                        forceCacheRefreshResult.getContext());
                    throw new MissingServerGroupException(
                        String.format("Server group '%s:%s' does not exist", region, it));
                  }
                }));
  }

  protected final List<Map<String, Object>> fetchServerGroups(
      String account,
      String cloudProvider,
      Map<String, List<String>> serverGroupsByRegion,
      Moniker moniker,
      boolean waitForUpServerGroup) {
    List<String> allServerGroups =
        serverGroupsByRegion.values().stream().flatMap(List::stream).collect(Collectors.toList());

    if (allServerGroups.size() > 1) {
      Names names = Names.parseName(allServerGroups.get(0));
      Optional.ofNullable(moniker).map(Moniker::getApp).orElseGet(names::getApp);
      String appName = Optional.ofNullable(moniker).map(Moniker::getApp).orElseGet(names::getApp);
      String clusterName =
          Optional.ofNullable(moniker).map(Moniker::getCluster).orElseGet(names::getCluster);
      Map<String, Object> cluster =
          cloudDriverService.getCluster(appName, account, clusterName, cloudProvider);

      return Optional.ofNullable((List<Map<String, Object>>) cluster.get("serverGroups"))
          .orElse(List.of());
    } else {
      // only one server group so no need to get the whole cluster
      Map.Entry<String, List<String>> onlyEntry = serverGroupsByRegion.entrySet().iterator().next();
      String region = onlyEntry.getKey();
      String serverGroupName = onlyEntry.getValue().get(0);

      try {
        Map<String, Object> response =
            cloudDriverService.getServerGroup(account, region, serverGroupName);
        return List.of(response);
      } catch (RetrofitError e) {
        Response response = e.getResponse();
        if (response != null && response.getStatus() != 404 || waitForUpServerGroup) {
          throw e;
        }
        throw new MissingServerGroupException(
            String.format("Server group '%s:%s' does not exist", region, serverGroupName));
      }
    }
  }

  public static class MissingServerGroupException extends IllegalStateException {
    public MissingServerGroupException(String message) {
      super(message);
    }
  }
}
