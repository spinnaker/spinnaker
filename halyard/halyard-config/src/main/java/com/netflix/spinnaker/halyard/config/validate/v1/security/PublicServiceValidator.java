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
 *
 *
 */

package com.netflix.spinnaker.halyard.config.validate.v1.security;

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.ERROR;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.security.PublicService;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.stereotype.Component;

@Component
public class PublicServiceValidator extends Validator<PublicService> {
  @Override
  public void validate(ConfigProblemSetBuilder p, PublicService n) {
    String overrideBaseUrl = n.getOverrideBaseUrl();
    if (!StringUtils.isEmpty(overrideBaseUrl)) {
      try {
        URI uri = new URIBuilder(overrideBaseUrl).build();

        if (StringUtils.isEmpty(uri.getScheme())) {
          p.addProblem(ERROR, "You must supply a URI scheme, e.g. 'http://' or 'https://'");
        }

        if (StringUtils.isEmpty(uri.getHost())) {
          p.addProblem(ERROR, "You must supply a URI host");
        }
      } catch (URISyntaxException e) {
        p.addProblem(ERROR, "Invalid base URL: " + e.getMessage());
      }
    }
  }
}
