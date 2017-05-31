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
 */

package com.netflix.spinnaker.halyard.config.model.v1.providers.aws;

import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class AwsProvider extends Provider<AwsAccount> {
  private String accessKeyId;
  private String secretAccessKey;

  private String defaultAssumeRole;
  private String defaultKeyPairTemplate = "{{name}}-keypair";

  private List<AwsRegion> defaultRegions = Collections.singletonList(new AwsRegion().setName("us-west-2"));
  private AwsDefaults defaults = new AwsDefaults();

  @Data
  public static class AwsRegion {
    String name;
  }

  @Data
  public static class AwsDefaults {
    String iamRole = "BaseIAMRole";
  }

  @Override
  public ProviderType providerType() {
    return ProviderType.AWS;
  }

  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }
}
