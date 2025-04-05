/*
 * Copyright 2017 Netflix, Inc.
 * Copyright 2022 Redbox Entertainment, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.gitlabci;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.build.BuildCache;
import com.netflix.spinnaker.igor.build.model.GenericProject;
import com.netflix.spinnaker.igor.config.GitlabCiProperties;
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline;
import com.netflix.spinnaker.igor.gitlabci.client.model.PipelineStatus;
import com.netflix.spinnaker.igor.gitlabci.client.model.Project;
import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiPipelineUtils;
import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiResultConverter;
import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiService;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.history.model.GenericBuildContent;
import com.netflix.spinnaker.igor.history.model.GenericBuildEvent;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor;
import com.netflix.spinnaker.igor.polling.DeltaItem;
import com.netflix.spinnaker.igor.polling.LockService;
import com.netflix.spinnaker.igor.polling.PollContext;
import com.netflix.spinnaker.igor.polling.PollingDelta;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty("gitlab-ci.enabled")
public class GitlabCiBuildMonitor
    extends CommonPollingMonitor<
        GitlabCiBuildMonitor.BuildDelta, GitlabCiBuildMonitor.BuildPollingDelta> {
  private static final int MAX_NUMBER_OF_PIPELINES = 5;

  private final BuildCache buildCache;
  private final BuildServices buildServices;
  private final GitlabCiProperties gitlabCiProperties;
  private final Optional<EchoService> echoService;

  @Autowired
  public GitlabCiBuildMonitor(
      IgorConfigurationProperties properties,
      Registry registry,
      DynamicConfigService dynamicConfigService,
      DiscoveryStatusListener discoveryStatusListener,
      Optional<LockService> lockService,
      BuildCache buildCache,
      BuildServices buildServices,
      GitlabCiProperties gitlabCiProperties,
      Optional<EchoService> echoService,
      TaskScheduler taskScheduler) {
    super(
        properties,
        registry,
        dynamicConfigService,
        discoveryStatusListener,
        lockService,
        taskScheduler);
    this.buildCache = buildCache;
    this.buildServices = buildServices;
    this.gitlabCiProperties = gitlabCiProperties;
    this.echoService = echoService;
  }

  @Override
  public void poll(boolean sendEvents) {
    buildServices.getServiceNames(BuildServiceProvider.GITLAB_CI).stream()
        .map(it -> new PollContext(it, !sendEvents))
        .forEach(this::pollSingle);
  }

  @Override
  protected BuildPollingDelta generateDelta(PollContext ctx) {
    final String master = ctx.partitionName;

    log.info("Checking for new builds for {}", kv("master", master));
    final AtomicInteger updatedBuilds = new AtomicInteger();
    final GitlabCiService gitlabCiService = (GitlabCiService) buildServices.getService(master);
    long startTime = System.currentTimeMillis();

    final List<Project> projects = gitlabCiService.getProjects();

    log.info(
        "Took {} ms to retrieve {} repositories with CI enabled (master: {})",
        System.currentTimeMillis() - startTime,
        projects.size(),
        kv("master", master));

    List<BuildDelta> delta = new ArrayList<>();
    projects.parallelStream()
        .forEach(
            project -> {
              // Gitlab Pipeline API is broken up by project, check for new pipelines for each
              // project individually
              List<Pipeline> pipelines =
                  filterOldPipelines(
                      gitlabCiService.getPipelines(project, MAX_NUMBER_OF_PIPELINES));
              for (Pipeline pipeline : pipelines) {
                if (pipeline.getStatus() != PipelineStatus.success) {
                  // Ignore pipelines that are running, pending, failed, etc.
                  continue;
                }
                String pipelineIdCacheKey = String.valueOf(project.getId());
                long cachedBuildId = buildCache.getLastBuild(master, pipelineIdCacheKey, false);
                // GitLab CI pipelineIds increment; Determine if it is new using the ID
                if (pipeline.getId() > cachedBuildId) {
                  updatedBuilds.incrementAndGet();
                  boolean isPipelineRunning = GitlabCiResultConverter.running(pipeline.getStatus());
                  delta.add(
                      new BuildDelta(pipelineIdCacheKey, project, pipeline, isPipelineRunning));
                }
              }
            });

    if (!delta.isEmpty()) {
      log.info(
          "Found {} new builds in {} milliseconds (master: {})",
          updatedBuilds.get(),
          System.currentTimeMillis() - startTime,
          kv("master", master));
    }

    return new BuildPollingDelta(delta, master, startTime);
  }

  @Override
  protected void commitDelta(BuildPollingDelta delta, boolean sendEvents) {
    int ttl = buildCacheJobTTLSeconds();
    final GitlabCiService gitlabCiService =
        (GitlabCiService) buildServices.getService(delta.master);

    log.info(
        "Last poll took {} ms (master: {})",
        System.currentTimeMillis() - delta.startTime,
        kv("master", delta.master));

    if (delta.items.isEmpty()) {
      return;
    }

    delta.items.parallelStream()
        .forEach(
            item -> {
              log.info(
                  "Build update [{}:{}:{}] [status:{}]",
                  kv("master", delta.master),
                  item.cacheKey,
                  item.pipeline.getId(),
                  item.pipeline.getStatus());
              buildCache.setLastBuild(
                  delta.master, item.cacheKey, item.pipeline.getId(), false, ttl);
              if (sendEvents) {
                sendEvent(item.project, item.pipeline, gitlabCiService.getAddress(), delta.master);
              }
            });
  }

  private List<Pipeline> filterOldPipelines(List<Pipeline> pipelines) {
    final Long threshold =
        new Date().getTime() - TimeUnit.DAYS.toMillis(gitlabCiProperties.getCachedJobTTLDays());
    return pipelines.stream()
        .filter(
            pipeline ->
                // Finished timestamp is not available without querying the pipelines individually
                // creates at is the closest timestamp value available in the list APIs
                (pipeline.getCreatedAt() != null)
                    && (pipeline.getCreatedAt().getTime() > threshold))
        .collect(Collectors.toList());
  }

  @Override
  public String getName() {
    return "gitlabCiBuildMonitor";
  }

  private int buildCacheJobTTLSeconds() {
    return (int) TimeUnit.DAYS.toSeconds(gitlabCiProperties.getCachedJobTTLDays());
  }

  private void sendEvent(Project project, Pipeline pipeline, String address, String master) {
    if (!echoService.isPresent()) {
      log.warn("Cannot send build notification: Echo is not enabled");
      registry.counter(missedNotificationId.withTag("monitor", getName())).increment();
      return;
    }

    String projectId = String.valueOf(project.getId());
    log.info("pushing event for {}:{}:{}", kv("master", master), projectId, pipeline.getId());
    GenericProject genericProject =
        new GenericProject(
            projectId,
            GitlabCiPipelineUtils.genericBuild(pipeline, address, project.getPathWithNamespace()));

    GenericBuildContent content = new GenericBuildContent();
    content.setMaster(master);
    content.setType(BuildServiceProvider.GITLAB_CI.name());
    content.setProject(genericProject);

    GenericBuildEvent event = new GenericBuildEvent();
    event.setContent(content);
    AuthenticatedRequest.allowAnonymous(
        () -> Retrofit2SyncCall.execute(echoService.get().postEvent(event)));
  }

  @Override
  protected Integer getPartitionUpperThreshold(String partition) {
    for (GitlabCiProperties.GitlabCiHost host : gitlabCiProperties.getMasters()) {
      if (host.getName() != null && host.getName().equals(partition)) {
        return host.getItemUpperThreshold();
      }
    }
    log.warn("Failed to find upper threshold for GitLabCI partition {}", partition);
    return null;
  }

  static class BuildPollingDelta implements PollingDelta<BuildDelta> {
    private final List<BuildDelta> items;
    private final String master;
    private final long startTime;

    public BuildPollingDelta(List<BuildDelta> items, String master, long startTime) {
      this.items = items;
      this.master = master;
      this.startTime = startTime;
    }

    @Override
    public List<BuildDelta> getItems() {
      return items;
    }
  }

  static class BuildDelta implements DeltaItem {
    private final String cacheKey;
    private final Project project;
    private final Pipeline pipeline;

    public BuildDelta(
        String cacheKey, Project project, Pipeline pipeline, boolean pipelineRunning) {
      this.cacheKey = cacheKey;
      this.project = project;
      this.pipeline = pipeline;
    }
  }
}
