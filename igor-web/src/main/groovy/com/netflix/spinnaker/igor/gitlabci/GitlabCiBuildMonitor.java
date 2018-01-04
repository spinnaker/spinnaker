/*
 * Copyright 2017 Netflix, Inc.
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

import com.netflix.spinnaker.igor.build.BuildCache;
import com.netflix.spinnaker.igor.build.model.GenericProject;
import com.netflix.spinnaker.igor.config.GitlabCiProperties;
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline;
import com.netflix.spinnaker.igor.gitlabci.client.model.Project;
import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiPipelineUtis;
import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiResultConverter;
import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiService;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.history.model.GenericBuildContent;
import com.netflix.spinnaker.igor.history.model.GenericBuildEvent;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor;
import com.netflix.spinnaker.igor.service.BuildMasters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import rx.Observable;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
@ConditionalOnProperty("gitlab-ci.enabled")
public class GitlabCiBuildMonitor extends CommonPollingMonitor {
    private static final int MAX_NUMBER_OF_PIPELINES = 5;

    @Autowired(required = false)
    private EchoService echoService;
    @Autowired
    private BuildCache buildCache;
    @Autowired
    private BuildMasters buildMasters;
    @Autowired
    private GitlabCiProperties gitlabCiProperties;

    @Override
    public void initialize() {
    }

    @Override
    public void poll() {
        buildMasters.filteredMap(BuildServiceProvider.GITLAB_CI).keySet()
            .forEach(this::changedBuilds);
    }

    void changedBuilds(final String master) {
        log.info("Checking for new builds for {}", kv("master", master));
        final AtomicInteger updatedBuilds = new AtomicInteger();
        final GitlabCiService gitlabCiService = (GitlabCiService) buildMasters.getMap().get(master);
        long startTime = System.currentTimeMillis();

        try {
            final List<Project> projects = gitlabCiService.getProjects();
            log.info("Took {} ms to retrieve {} repositories (master: {})", System.currentTimeMillis() - startTime, projects.size(), kv("master", master));
            Observable.from(projects).subscribe(
                project -> {
                    List<Pipeline> pipelines = filterOldPipelines(gitlabCiService.getPipelines(project, MAX_NUMBER_OF_PIPELINES));
                    for (Pipeline pipeline : pipelines) {
                        String branchedRepoSlug = GitlabCiPipelineUtis.getBranchedPipelineSlug(project, pipeline);

                        boolean isPipelineRunning = GitlabCiResultConverter.running(pipeline.getStatus());
                        int cachedBuildId = buildCache.getLastBuild(master, branchedRepoSlug, isPipelineRunning);
                        // In case of Gitlab CI the pipeline ids are increasing so we can use it for ordering
                        if (pipeline.getId() > cachedBuildId) {
                            updatedBuilds.incrementAndGet();
                            log.info("Build update [{}:{}:{}] [status:{}] [running:{}]", kv("master", master), branchedRepoSlug, pipeline.getId(), pipeline.getStatus(), isPipelineRunning);
                            buildCache.setLastBuild(master, branchedRepoSlug, pipeline.getId(), isPipelineRunning, buildCacheJobTTLSeconds());
                            buildCache.setLastBuild(master, project.getPathWithNamespace(), pipeline.getId(), isPipelineRunning, buildCacheJobTTLSeconds());
                            sendEventForPipeline(project, pipeline, gitlabCiService.getAddress(), branchedRepoSlug, master);
                        }
                    }
                },
                throwable -> log.error(String.format("Error: %s (master: %s)", throwable, kv("master", master)), throwable));
            if (updatedBuilds.get() > 0) {
                log.info("Found {} new builds (master: {})", updatedBuilds.get(), kv("master", master));
            }

            log.info("Last poll took {} ms (master: {})", System.currentTimeMillis() - startTime, kv("master", master));
        } catch (Exception e) {
            log.error("Failed to obtain the list of projects", e);
        }
    }

    private List<Pipeline> filterOldPipelines(List<Pipeline> pipelines) {
        final Long threshold = new Date().getTime() - TimeUnit.DAYS.toMillis(gitlabCiProperties.getCachedJobTTLDays());
        return pipelines.stream().filter(pipeline ->
            (pipeline.getFinishedAt() != null) && (pipeline.getFinishedAt().getTime() > threshold)
        ).collect(Collectors.toList());
    }

    @Override
    public String getName() {
        return "gitlabCiBuildMonitor";
    }

    private int buildCacheJobTTLSeconds() {
        return (int) TimeUnit.DAYS.toSeconds(gitlabCiProperties.getCachedJobTTLDays());
    }

    private void sendEventForPipeline(Project project, final Pipeline pipeline, String address, final String branchedSlug, String master) {
        if (echoService != null) {
            sendEvent(pipeline.getRef(), project, pipeline, address, master);
            sendEvent(branchedSlug, project, pipeline, address, master);
        }
    }

    private void sendEvent(String slug, Project project, Pipeline pipeline, String address, String master) {
        log.info("pushing event for {}:{}:{}", kv("master", master), slug, pipeline.getId());
        GenericProject genericProject = new GenericProject(slug, GitlabCiPipelineUtis.genericBuild(pipeline, project.getPathWithNamespace(), address));

        GenericBuildContent content = new GenericBuildContent();
        content.setMaster(master);
        content.setType("gitlab-ci");
        content.setProject(genericProject);

        GenericBuildEvent event = new GenericBuildEvent();
        event.setContent(content);
        echoService.postEvent(event);
    }

    public void setEchoService(EchoService echoService) {
        this.echoService = echoService;
    }

    public void setBuildCache(BuildCache buildCache) {
        this.buildCache = buildCache;
    }

    public void setBuildMasters(BuildMasters buildMasters) {
        this.buildMasters = buildMasters;
    }

    public void setGitlabCiProperties(GitlabCiProperties gitlabCiProperties) {
        this.gitlabCiProperties = gitlabCiProperties;
    }
}
