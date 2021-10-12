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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.aws;

public class AwsCommandProperties {
  public static final String DEFAULT_KEY_PAIR_DESCRIPTION =
      "Provide the name of the AWS key-pair to use. "
          + "See http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html for more information.";

  public static final String EDDA_DESCRIPTION =
      "The endpoint Edda is reachable at. Edda is not a hard dependency of Spinnaker, "
          + "but is helpful for reducing the request volume against AWS. "
          + "See https://github.com/Netflix/edda for more information.";

  public static final String DISCOVERY_DESCRIPTION =
      "The endpoint your Eureka discovery system is reachable at. "
          + "See https://github.com/Netflix/eureka for more information.\n\n"
          + "Example: http://{{region}}.eureka.url.to.use:8080/eureka-server/v2 \n\nUsing {{region}} will make Spinnaker "
          + "use AWS regions in the hostname to access discovery so that you can have discovery for multiple regions.";

  public static final String ACCOUNT_ID_DESCRIPTION =
      "Your AWS account ID to manage. "
          + "See http://docs.aws.amazon.com/IAM/latest/UserGuide/console_account-alias.html for more information.";

  public static final String REGIONS_DESCRIPTION =
      "The AWS regions this Spinnaker account will manage.";

  public static final String ASSUME_ROLE_DESCRIPTION =
      "If set, Halyard will configure a credentials provider that uses AWS "
          + "Security Token Service to assume the specified role.\n\n"
          + "Example: \"user/spinnaker\" or \"role/spinnakerManaged\"";

  public static final String EXTERNAL_ID_DESCRIPTION =
      "Optional parameter used to identify and control access to AWS resources. "
          + "Set this to the same value as the ExternalID parameter in the trust policy for the role you want to assume.";

  public static final String ACCESS_KEY_ID_DESCRIPTION =
      "Your AWS Access Key ID. If not provided, Halyard/Spinnaker will try to find AWS credentials "
          + "as described at http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html#credentials-default";

  public static final String SECRET_KEY_DESCRIPTION = "Your AWS Secret Key.";

  public static final String HOOK_DEFAULT_VALUE_DESCRIPTION =
      "Defines the action the Auto Scaling group should take when "
          + "the lifecycle hook timeout elapses or if an unexpected failure occurs. This parameter can be either CONTINUE or ABANDON. "
          + "The default value is ABANDON.";

  public static final String HOOK_HEARTBEAT_TIMEOUT_DESCRIPTION =
      "Set the heartbeat timeout for the lifecycle hook. Instances can \" +\n"
          + "          \"remain in a wait state for a finite period of time. The default is one hour (3600 seconds).";

  public static final String HOOK_NOTIFICATION_TARGET_ARN =
      "The ARN of the notification target that Amazon EC2 Auto Scaling "
          + "uses to notify you when an instance is in the transition state for the lifecycle hook. This target can be either "
          + "an SQS queue or an SNS topic.\n";

  public static final String HOOK_ROLE_ARN_DESCRIPTION =
      "The ARN of the IAM role that allows the Auto Scaling group "
          + "to publish to the specified notification target, for example, an Amazon SNS topic or an Amazon SQS queue.";

  public static final String LAMBDA_ENABLED = "Enables Lambda Functions in the account";
}
