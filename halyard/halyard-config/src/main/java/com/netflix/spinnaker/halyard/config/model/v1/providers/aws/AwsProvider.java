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

import com.netflix.spinnaker.halyard.config.model.v1.node.HasImageProvider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Secret;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AwsProvider extends HasImageProvider<AwsAccount, AwsBakeryDefaults>
    implements Cloneable {
  private String accessKeyId;
  @Secret String secretAccessKey;

  private String defaultAssumeRole;
  private String defaultKeyPairTemplate = "{{name}}-keypair";

  private List<AwsRegion> defaultRegions =
      Collections.singletonList(new AwsRegion().setName("us-west-2"));
  private AwsDefaults defaults = new AwsDefaults();
  private Features features;
  private Lambda lambda;

  @Data
  public static class AwsRegion {
    String name;
  }

  @Data
  public static class AwsLifecycleHook {
    String defaultResult;
    Integer heartbeatTimeout;
    String lifecycleTransition;
    String notificationTargetARN;
    String roleARN;
  }

  @Data
  public static class AwsDefaults {
    String iamRole = "BaseIAMRole";
  }

  @Data
  public static class Lambda {

    public Lambda() {}

    public Lambda(boolean enable) {
      this.enabled = enable;
    }

    Boolean enabled;
  }

  @Data
  public static class Features {

    public Features() {}

    public Features(CloudFormation cloudFormation, Lambda lambda) {
      this.cloudFormation = cloudFormation;
      this.lambda = lambda;
    }

    CloudFormation cloudFormation;
    Lambda lambda;

    @Data
    public static class CloudFormation {
      public CloudFormation() {}

      public CloudFormation(Boolean enabled) {
        this.enabled = enabled;
      }

      Boolean enabled;
    }

    @Data
    public static class Lambda {
      public Lambda() {}

      public Lambda(Boolean enabled) {
        this.enabled = enabled;
      }

      Boolean enabled;
    }
  }

  @Override
  public ProviderType providerType() {
    return ProviderType.AWS;
  }

  @Override
  public AwsBakeryDefaults emptyBakeryDefaults() {
    return new AwsBakeryDefaults();
  }
}
