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
package com.netflix.spinnaker.kork.test.mimicker.producers.aws;

import com.netflix.spinnaker.kork.test.mimicker.producers.RandomProducer;
import com.netflix.spinnaker.kork.test.mimicker.producers.TextProducer;
import java.util.Arrays;
import java.util.regex.Pattern;

/** TODO(rz): Obviously a whole lot more that need to be added... */
public class AwsArnProducer {

  private static final String PARTITION = "{{PARTITION}}";
  private static final String REGION = "{{REGION}}";
  private static final String ACCOUNT = "{{ACCOUNT}}";
  private static final String UUID = "{{UUID}}";
  private static final String DASHED_ID = "{{DASHED_ID}}";
  private static final String AWS_ID = "{{AWS_ID}}";

  private final AwsProducer awsProducer;
  private final TextProducer textProducer;
  private final RandomProducer randomProducer;

  public AwsArnProducer(
      AwsProducer awsProducer, TextProducer textProducer, RandomProducer randomProducer) {
    this.awsProducer = awsProducer;
    this.textProducer = textProducer;
    this.randomProducer = randomProducer;
  }

  private String render(String template) {
    return template
        .replaceAll(Pattern.quote(PARTITION), getPartition())
        .replaceAll(Pattern.quote(REGION), awsProducer.getRegion())
        .replaceAll(Pattern.quote(ACCOUNT), awsProducer.getAccountId())
        .replaceAll(Pattern.quote(UUID), randomProducer.uuid())
        .replaceAll(Pattern.quote(DASHED_ID), textProducer.dashedWords(1, 6))
        .replaceAll(Pattern.quote(AWS_ID), randomProducer.numeric(17));
  }

  public String getPartition() {
    return randomProducer.element(Arrays.asList("aws", "aws-cn", "aws-us-gov"));
  }

  public String getAutoscalingPolicy() {
    return render(
        "arn:"
            + PARTITION
            + ":autoscaling:"
            + REGION
            + ":"
            + ACCOUNT
            + ":scalingPolicy:"
            + UUID
            + ":autoScalingGroupName/"
            + DASHED_ID
            + ":policyName/"
            + DASHED_ID);
  }

  public class Ec2Arns {

    public String getInstance() {
      return render("arn:" + PARTITION + ":ec2:" + REGION + ":" + ACCOUNT + ":instance/" + AWS_ID);
    }
  }
}
