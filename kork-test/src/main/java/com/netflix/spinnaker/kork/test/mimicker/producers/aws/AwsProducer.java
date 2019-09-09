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

import static java.lang.String.format;

import com.netflix.spinnaker.kork.test.mimicker.DataContainer;
import com.netflix.spinnaker.kork.test.mimicker.producers.RandomProducer;
import com.netflix.spinnaker.kork.test.mimicker.producers.TextProducer;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Basic AWS values.
 *
 * <p>This producer must not include AWS-specific models (so not to pollute the test classpath). If
 * your tests need to generate fake AWS models, create a Producer for that service (e.g.
 * clouddriver).
 */
public class AwsProducer {

  private final DataContainer dataContainer;
  private final TextProducer textProducer;
  private final RandomProducer randomProducer;

  public AwsProducer(
      DataContainer dataContainer, TextProducer textProducer, RandomProducer randomProducer) {
    this.dataContainer = dataContainer;
    this.textProducer = textProducer;
    this.randomProducer = randomProducer;

    dataContainer.load("mimicker-aws.yml");
  }

  @NotNull
  public String getRegion() {
    return dataContainer.random("aws/regions");
  }

  @NotNull
  public List<@NotNull String> getAvailabilityZones() {
    return dataContainer.list(format("aws/availabilityZones/%s", getRegion()));
  }

  @NotNull
  public List<@NotNull String> getAvailabilityZones(String region) {
    return dataContainer.list(format("aws/availabilityZones/%s", region));
  }

  @NotNull
  public String getAvailabilityZone() {
    return randomProducer.element(getAvailabilityZones());
  }

  @NotNull
  public String getAvailabilityZone(String region) {
    return randomProducer.element(getAvailabilityZones(region));
  }

  @NotNull
  public String getIdentifier(String prefix) {
    return getIdentifier(prefix, 17);
  }

  @NotNull
  public String getIdentifier(String prefix, int length) {
    return format("%s-%s", prefix, randomProducer.alphanumeric(length));
  }

  @NotNull
  public String getAmiId() {
    return getIdentifier("ami");
  }

  @NotNull
  public String getVpcId() {
    return getIdentifier("vpc");
  }

  @NotNull
  public String getSubnetId() {
    return getIdentifier("subnet");
  }

  @NotNull
  public String getInstanceId() {
    return getIdentifier("i");
  }

  @NotNull
  public String getVolumeId() {
    return getIdentifier("vol");
  }

  @NotNull
  public String snapshotId() {
    return getIdentifier("snap");
  }

  @NotNull
  public String getEipId() {
    return getIdentifier("eipalloc");
  }

  @NotNull
  public String getEniId() {
    return getIdentifier("eni");
  }

  @NotNull
  public String getSecurityGroupId() {
    return getIdentifier("sg");
  }

  public String getInstanceType() {
    return dataContainer.random("mimic.aws.instanceTypes");
  }

  @NotNull
  public String getAccountId() {
    // Can return 0 as the first character, which is fair play according to AWS
    return randomProducer.numeric(12);
  }

  public AwsArnProducer getArns() {
    return new AwsArnProducer(this, textProducer, randomProducer);
  }
}
