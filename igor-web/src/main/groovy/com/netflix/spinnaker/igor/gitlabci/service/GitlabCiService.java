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
package com.netflix.spinnaker.igor.gitlabci.service;

import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import com.netflix.spinnaker.igor.gitlabci.client.GitlabCiClient;
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline;
import com.netflix.spinnaker.igor.gitlabci.client.model.PipelineSummary;
import com.netflix.spinnaker.igor.gitlabci.client.model.Project;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;
import com.netflix.spinnaker.igor.service.BuildService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GitlabCiService implements BuildService {
    private GitlabCiClient client;
    private String address;
    private boolean limitByMembership;
    private boolean limitByOwnership;

    public GitlabCiService(GitlabCiClient client, String address, boolean limitByMembership, boolean limitByOwnership) {
        this.client = client;
        this.address = address;
        this.limitByMembership = limitByMembership;
        this.limitByOwnership = limitByOwnership;
    }

    @Override
    public BuildServiceProvider buildServiceProvider() {
        return BuildServiceProvider.GITLAB_CI;
    }

    @Override
    public List<GenericGitRevision> getGenericGitRevisions(String job, int buildNumber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GenericBuild getGenericBuild(String job, int buildNumber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int triggerBuildWithParameters(String job, Map<String, String> queryParameters) {
        throw new UnsupportedOperationException();
    }

    public List<Project> getProjects() {
        return getProjectsRec(new ArrayList<>(), 1);
    }

    public List<Pipeline> getPipelines(final Project project, int limit) {
        isValidPageSize(limit);

        List<PipelineSummary> pipelineSummaries = client.getPipelineSummaries(project.getId(), limit);

        return pipelineSummaries.stream()
            .map((PipelineSummary ps) -> client.getPipeline(project.getId(), ps.getId()))
            .collect(Collectors.toList());
    }

    public String getAddress() {
        return address;
    }

    private List<Project> getProjectsRec(List<Project> projects, int page) {
        List<Project> slice = client.getProjects(limitByMembership, limitByOwnership, page);
        if (slice.isEmpty()) {
            return projects;
        } else {
            projects.addAll(slice);
            return getProjectsRec(projects, page + 1);
        }
    }

    private static void isValidPageSize(int perPage) {
        if (perPage > GitlabCiClient.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Gitlab API call page size should be less than " + String.valueOf(GitlabCiClient.MAX_PAGE_SIZE) + " but was " + String.valueOf(perPage));
        }
    }
}
