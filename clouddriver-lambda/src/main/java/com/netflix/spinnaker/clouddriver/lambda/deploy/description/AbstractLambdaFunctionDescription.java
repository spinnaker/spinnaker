/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.clouddriver.lambda.deploy.description;

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription;
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class AbstractLambdaFunctionDescription extends AbstractAmazonCredentialsDescription
    implements ApplicationNameable {
  String region;
  String appName;

  @Override
  public Collection<String> getApplications() {
    if (appName == null || appName.isEmpty()) {
      return Collections.emptyList();
    }
    return List.of(getAppName());
  }
}
