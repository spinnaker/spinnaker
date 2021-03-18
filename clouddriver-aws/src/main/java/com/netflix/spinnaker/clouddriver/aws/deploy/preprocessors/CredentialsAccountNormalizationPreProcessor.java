/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.aws.deploy.preprocessors;

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AllowLaunchDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationDescriptionPreProcessor;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Normalizes the use of `account` vs `credentials`, ensuring that both are always set; prefers the
 * value from `credentials`.
 */
@Slf4j
@Component
public class CredentialsAccountNormalizationPreProcessor
    implements AtomicOperationDescriptionPreProcessor {
  @Override
  public boolean supports(Class descriptionClass) {
    return !AllowLaunchDescription.class.isAssignableFrom(descriptionClass);
  }

  @Override
  public Map process(Map description) {
    final String account = (String) description.get("account");
    final String credentials = (String) description.get("credentials");

    if (account != null && credentials != null && !account.equals(credentials)) {
      log.warn(
          "Passed 'account' ({}) and 'credentials' ({}), but values are not equal",
          account,
          credentials);
      description.put("account", credentials);
    }

    if (credentials == null && account != null) {
      description.put("credentials", account);
    }
    if (account == null && credentials != null) {
      description.put("account", credentials);
    }

    return description;
  }
}
