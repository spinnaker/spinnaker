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

package com.netflix.spinnaker.igor.gcb;

import com.google.api.services.cloudbuild.v1.model.Build;
import com.google.api.services.cloudbuild.v1.model.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ConditionalOnProperty("gcb.enabled")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/gcb")
public class GoogleCloudBuildController {
  private final GoogleCloudBuildAccountRepository googleCloudBuildAccountRepository;

  @RequestMapping(value = "/accounts", method = RequestMethod.GET)
  List<String> getAccounts() {
    return googleCloudBuildAccountRepository.getAccounts();
  }

  @RequestMapping(value = "/builds/create/{account}", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
  Operation createBuild(@PathVariable String account, @RequestBody Build build) {
    return googleCloudBuildAccountRepository.getGoogleCloudBuild(account).createBuild(build);
  }
}
