/*
 * Copyright 2020 Amazon.com, Inc.
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

package com.netflix.spinnaker.halyard.controllers.v1.ci;

import com.netflix.spinnaker.halyard.config.model.v1.ci.codebuild.AwsCodeBuild;
import com.netflix.spinnaker.halyard.config.model.v1.ci.codebuild.AwsCodeBuildAccount;
import com.netflix.spinnaker.halyard.config.services.v1.ci.AwsCodeBuildService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/ci/codebuild")
public class AwsCodeBuildController extends CiController<AwsCodeBuildAccount, AwsCodeBuild> {
  public AwsCodeBuildController(
      CiController.Members members, AwsCodeBuildService awsCodeBuildService) {
    super(members, awsCodeBuildService);
  }
}
