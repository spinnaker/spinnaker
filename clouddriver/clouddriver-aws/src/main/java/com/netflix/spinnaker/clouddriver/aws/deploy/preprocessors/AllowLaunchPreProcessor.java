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
import org.springframework.stereotype.Component;

@Component
public class AllowLaunchPreProcessor implements AtomicOperationDescriptionPreProcessor {
  @Override
  public boolean supports(Class descriptionClass) {
    return AllowLaunchDescription.class.isAssignableFrom(descriptionClass);
  }

  @Override
  public Map process(Map description) {
    // Backwards-compatibility from when AllowLaunch used to overload "account" from the abstract
    // AWS description.
    description.putIfAbsent("targetAccount", description.get("account"));
    description.put("account", description.get("credentials"));
    return description;
  }
}
