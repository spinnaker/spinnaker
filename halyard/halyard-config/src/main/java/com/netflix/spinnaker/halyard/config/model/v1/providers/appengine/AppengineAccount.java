/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.providers.appengine;

import com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.Secret;
import com.netflix.spinnaker.halyard.config.model.v1.node.SecretFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.ValidForSpinnakerVersion;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.CommonGoogleAccount;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AppengineAccount extends CommonGoogleAccount {
  private String localRepositoryDirectory;
  private String gitHttpsUsername;
  @Secret private String gitHttpsPassword;
  @Secret private String githubOAuthAccessToken;
  @LocalFile @SecretFile private String sshPrivateKeyFilePath;
  @Secret private String sshPrivateKeyPassphrase;
  @LocalFile @SecretFile private String sshKnownHostsFilePath;
  private boolean sshTrustUnknownHosts;

  @ValidForSpinnakerVersion(
      lowerBound = "1.6.0",
      tooLowMessage =
          "The gcloud release track that Spinnaker will use when deploying to App Engine"
              + " is not configurable prior to this release.")
  private GcloudReleaseTrack gcloudReleaseTrack;

  @ValidForSpinnakerVersion(
      lowerBound = "1.11.0",
      tooLowMessage =
          "The set of services that Spinnaker will index is not configurable prior to this release.")
  private List<String> services;

  @ValidForSpinnakerVersion(
      lowerBound = "1.11.0",
      tooLowMessage =
          "The set of versions that Spinnaker will index is not configurable prior to this release.")
  private List<String> versions;

  @ValidForSpinnakerVersion(
      lowerBound = "1.11.0",
      tooLowMessage =
          "The set of services that Spinnaker will ignore is not configurable prior to this release.")
  private List<String> omitServices;

  @ValidForSpinnakerVersion(
      lowerBound = "1.11.0",
      tooLowMessage =
          "The set of versions that Spinnaker will ignore is not configurable prior to this release.")
  private List<String> omitVersions;

  @ValidForSpinnakerVersion(
      lowerBound = "1.13.0",
      tooLowMessage =
          "The AppEngine provider's caching interval is not configurable prior to this release.")
  private Long cachingIntervalSeconds;

  public enum GcloudReleaseTrack {
    ALPHA,
    BETA,
    STABLE;
  }
}
