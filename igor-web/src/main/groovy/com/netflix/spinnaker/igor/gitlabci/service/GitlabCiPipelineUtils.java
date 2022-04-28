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

public class GitlabCiPipelineUtils {

  public static GenericBuild genericBuild(
      Pipeline pipeline, String baseUrl, String repoPathWithNamespace) {
    String friendlyName = String.format("%s (%s)", pipeline.getRef(), pipeline.getId());
    GenericBuild genericBuild = new GenericBuild();
    genericBuild.setBuilding(GitlabCiResultConverter.running(pipeline.getStatus()));
    genericBuild.setNumber(pipeline.getId());
    genericBuild.setResult(
        GitlabCiResultConverter.getResultFromGitlabCiState(pipeline.getStatus()));
    genericBuild.setName(friendlyName);
    genericBuild.setUrl(url(repoPathWithNamespace, baseUrl, pipeline.getId()));
    genericBuild.setFullDisplayName(friendlyName);
    genericBuild.setId(String.valueOf(pipeline.getId()));
    return genericBuild;
  }

  private static String url(final String repoSlug, final String baseUrl, final int id) {
    return baseUrl + "/" + repoSlug + "/pipelines/" + String.valueOf(id);
  }
}
