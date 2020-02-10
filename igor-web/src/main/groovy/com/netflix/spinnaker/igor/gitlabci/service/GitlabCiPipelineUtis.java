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
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline;
import com.netflix.spinnaker.igor.gitlabci.client.model.Project;

public class GitlabCiPipelineUtis {
  public static String getBranchedPipelineSlug(final Project project, final Pipeline pipeline) {
    return pipeline.isTag()
        ? project.getPathWithNamespace() + "/tags"
        : project.getPathWithNamespace() + "/" + pipeline.getRef();
  }

  public static GenericBuild genericBuild(Pipeline pipeline, String repoSlug, String baseUrl) {
    GenericBuild.GenericBuildBuilder builder =
        GenericBuild.builder()
            .building(GitlabCiResultConverter.running(pipeline.getStatus()))
            .number(pipeline.getId())
            .duration(pipeline.getDuration())
            .result(GitlabCiResultConverter.getResultFromGitlabCiState(pipeline.getStatus()))
            .name(repoSlug)
            .url(url(repoSlug, baseUrl, pipeline.getId()));

    if (pipeline.getFinishedAt() != null) {
      builder.timestamp(Long.toString(pipeline.getFinishedAt().getTime()));
    }

    return builder.build();
  }

  private static String url(final String repoSlug, final String baseUrl, final int id) {
    return baseUrl + "/" + repoSlug + "/pipelines/" + id;
  }
}
