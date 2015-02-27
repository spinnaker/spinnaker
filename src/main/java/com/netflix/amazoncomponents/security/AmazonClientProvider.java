/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.amazoncomponents.security;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.route53.AmazonRoute53;
import com.netflix.edda.AwsClientFactory;
import com.netflix.edda.AwsConfiguration;
import com.netflix.ie.config.Configuration;
import com.netflix.ie.config.MapConfiguration;
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Collections;

/**
 * Adapts edda-client object factory to use NetflixAmazonCredentials.
 */
public class AmazonClientProvider {


  public AmazonClientProvider() {
  }


  /**
   * When edda serves the request, http headers are captured and available to the calling thread.
   * Header names are lower-case.
   * @return the HTTP headers from the last response for the requesting thread, if available.
   */
  public Map<String, List<String>> getLastResponseHeaders() {
    return Collections.<String, List<String>>emptyMap();
  }

  public AmazonEC2 getAmazonEC2(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonEC2(amazonCredentials, region, false);
  }

  private AwsConfiguration getConfiguration(NetflixAmazonCredentials creds, boolean skipEdda, String region) {
    final String prefix = null;
    Map<String, String> props = new HashMap<>();
    props.put("useEdda", Boolean.toString(!skipEdda && creds.getEddaEnabled()));
    props.put("wrapAwsClient", "true");
    if (creds.getEddaEnabled()) {
      props.put("url", String.format(creds.getEdda(), region));
    }
    MapConfiguration conf = new MapConfiguration(prefix, props);
    return Configuration.newProxyImpl(AwsConfiguration.class, prefix, conf);
  }

  public AmazonEC2 getAmazonEC2(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return AwsClientFactory.newEc2Client(getConfiguration(amazonCredentials, skipEdda, region), amazonCredentials.getCredentialsProvider(), region);
  }


  public AmazonAutoScaling getAutoScaling(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAutoScaling(amazonCredentials, region, false);
  }

  public AmazonRoute53 getAmazonRoute53(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonRoute53(amazonCredentials, region, false);
  }

  public AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonElasticLoadBalancing(amazonCredentials, region, false);
  }

  public AmazonCloudWatch getAmazonCloudWatch(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonCloudWatch(amazonCredentials, region, false);
  }

  public AmazonAutoScaling getAutoScaling(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return AwsClientFactory.newAutoScalingClient(getConfiguration(amazonCredentials, skipEdda, region), amazonCredentials.getCredentialsProvider(), region);
  }

  public AmazonRoute53 getAmazonRoute53(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return AwsClientFactory.newRoute53Client(getConfiguration(amazonCredentials, skipEdda, region), amazonCredentials.getCredentialsProvider(), region);
  }

  public AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return AwsClientFactory.newElasticLoadBalancingClient(getConfiguration(amazonCredentials, skipEdda, region), amazonCredentials.getCredentialsProvider(), region);
  }


  public AmazonCloudWatch getAmazonCloudWatch(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return AwsClientFactory.newCloudWatchClient(getConfiguration(amazonCredentials, skipEdda, region), amazonCredentials.getCredentialsProvider(), region);
  }

  private static void checkCredentials(NetflixAmazonCredentials amazonCredentials) {
    if (amazonCredentials == null) {
      throw new IllegalArgumentException("Credentials cannot be null");
    }
  }

}

