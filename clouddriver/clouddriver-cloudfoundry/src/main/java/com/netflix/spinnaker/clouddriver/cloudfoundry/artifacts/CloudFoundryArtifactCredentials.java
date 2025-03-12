/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.artifacts;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.InputStream;
import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CloudFoundryArtifactCredentials implements ArtifactCredentials {
  public static final String ARTIFACTS_TYPE = "artifacts/cloudfoundry";
  public static final String TYPE = "cloudfoundry/app";

  private final String name = "cloudfoundry";
  private final CloudFoundryClient client;

  @Override
  @Nonnull
  public ImmutableList<String> getTypes() {
    return ImmutableList.of(TYPE);
  }

  @Override
  @Nonnull
  public InputStream download(@Nonnull Artifact artifact) {
    String packageId = client.getApplications().findCurrentPackageIdByAppId(artifact.getUuid());
    return client.getApplications().downloadPackageBits(packageId);
  }

  @Override
  public String getType() {
    return ARTIFACTS_TYPE;
  }
}
