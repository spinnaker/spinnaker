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

package com.netflix.spinnaker.igor.codebuild;

import com.amazonaws.services.codebuild.model.Build;
import com.amazonaws.services.codebuild.model.StartBuildRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@ConditionalOnProperty("codebuild.enabled")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/codebuild")
public class AwsCodeBuildController {
  private final AwsCodeBuildAccountRepository awsCodeBuildAccountRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @RequestMapping(value = "/accounts", method = RequestMethod.GET)
  List<String> getAccounts() {
    return awsCodeBuildAccountRepository.getAccountNames();
  }

  @RequestMapping(
      value = "/builds/start/{account}",
      method = RequestMethod.POST,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  Build startBuild(@PathVariable String account, @RequestBody Map<String, Object> requestBody) {
    return awsCodeBuildAccountRepository
        .getAccount(account)
        .startBuild(objectMapper.convertValue(requestBody, StartBuildRequest.class));
  }

  @RequestMapping(value = "/builds/{account}/{buildId}", method = RequestMethod.GET)
  Build getBuild(@PathVariable String account, @PathVariable String buildId) {
    return awsCodeBuildAccountRepository.getAccount(account).getBuild(buildId);
  }

  @RequestMapping(value = "/builds/stop/{account}/{buildId}", method = RequestMethod.POST)
  Build stopBuild(@PathVariable String account, @PathVariable String buildId) {
    return awsCodeBuildAccountRepository.getAccount(account).stopBuild(buildId);
  }
}
