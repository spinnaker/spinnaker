/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.igor.travis.service;

import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.travis.client.model.Build;
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildState;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build;

public class TravisBuildConverter {
  public static GenericBuild genericBuild(Build build, String repoSlug, String baseUrl) {
    GenericBuild genericBuild = new GenericBuild();
    genericBuild.setBuilding(build.getState() == TravisBuildState.started);
    genericBuild.setNumber(build.getNumber());
    genericBuild.setDuration(build.getDuration());
    genericBuild.setResult(build.getState().getResult());
    genericBuild.setName(repoSlug);
    genericBuild.setUrl(url(repoSlug, baseUrl, build.getId()));
    if (build.getFinishedAt() != null) {
      genericBuild.setTimestamp(String.valueOf(build.getTimestamp()));
    }
    return genericBuild;
  }

  public static GenericBuild genericBuild(V3Build build, String baseUrl) {
    GenericBuild genericBuild = new GenericBuild();
    genericBuild.setBuilding(build.getState() == TravisBuildState.started);
    genericBuild.setNumber(build.getNumber());
    genericBuild.setDuration(build.getDuration());
    genericBuild.setResult(build.getState().getResult());
    genericBuild.setName(build.getRepository().getSlug());
    genericBuild.setUrl(url(build.getRepository().getSlug(), baseUrl, build.getId()));
    if (build.getFinishedAt() != null) {
      genericBuild.setTimestamp(String.valueOf(build.getTimestamp()));
    }
    return genericBuild;
  }

  public static String url(String repoSlug, String baseUrl, int id) {
    return baseUrl + "/" + repoSlug + "/builds/" + id;
  }
}
