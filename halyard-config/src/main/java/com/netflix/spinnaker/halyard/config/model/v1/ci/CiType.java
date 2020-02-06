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

package com.netflix.spinnaker.halyard.config.model.v1.ci;

import com.netflix.spinnaker.halyard.config.model.v1.ci.codebuild.AwsCodeBuild;
import com.netflix.spinnaker.halyard.config.model.v1.ci.codebuild.AwsCodeBuildAccount;
import com.netflix.spinnaker.halyard.config.model.v1.ci.concourse.ConcourseCi;
import com.netflix.spinnaker.halyard.config.model.v1.ci.concourse.ConcourseMaster;
import com.netflix.spinnaker.halyard.config.model.v1.ci.gcb.GoogleCloudBuild;
import com.netflix.spinnaker.halyard.config.model.v1.ci.gcb.GoogleCloudBuildAccount;
import com.netflix.spinnaker.halyard.config.model.v1.ci.jenkins.JenkinsCi;
import com.netflix.spinnaker.halyard.config.model.v1.ci.jenkins.JenkinsMaster;
import com.netflix.spinnaker.halyard.config.model.v1.ci.travis.TravisCi;
import com.netflix.spinnaker.halyard.config.model.v1.ci.travis.TravisMaster;
import com.netflix.spinnaker.halyard.config.model.v1.ci.wercker.WerckerCi;
import com.netflix.spinnaker.halyard.config.model.v1.ci.wercker.WerckerMaster;
import com.netflix.spinnaker.halyard.config.model.v1.node.CIAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.Ci;

public enum CiType {
  jenkins(JenkinsCi.class, JenkinsMaster.class),
  travis(TravisCi.class, TravisMaster.class),
  wercker(WerckerCi.class, WerckerMaster.class),
  concourse(ConcourseCi.class, ConcourseMaster.class),
  gcb(GoogleCloudBuild.class, GoogleCloudBuildAccount.class),
  codebuild(AwsCodeBuild.class, AwsCodeBuildAccount.class);

  public final Class<? extends Ci> ciClass;
  public final Class<? extends CIAccount> accountClass;

  CiType(Class<? extends Ci> ciClass, Class<? extends CIAccount> accountClass) {
    this.ciClass = ciClass;
    this.accountClass = accountClass;
  }

  public static CiType getCiType(String ciType) {
    try {
      return CiType.valueOf(ciType);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format(
              "No Continous Integration service with name '%s' handled by halyard", ciType));
    }
  }
}
