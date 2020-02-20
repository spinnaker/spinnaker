/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.front50.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Creates an S3 client.
 *
 * <p>Since there are multiple implementations of {@link S3Properties} and we create different
 * clients based on those properties, the actual factory code needed to be split out.
 */
public class S3ClientFactory {

  public static AmazonS3 create(
      AWSCredentialsProvider awsCredentialsProvider, S3Properties s3Properties) {
    ClientConfiguration clientConfiguration = new ClientConfiguration();
    if (s3Properties.getProxyProtocol() != null) {
      if (s3Properties.getProxyProtocol().equalsIgnoreCase("HTTPS")) {
        clientConfiguration.setProtocol(Protocol.HTTPS);
      } else {
        clientConfiguration.setProtocol(Protocol.HTTP);
      }
      Optional.ofNullable(s3Properties.getProxyHost()).ifPresent(clientConfiguration::setProxyHost);
      Optional.ofNullable(s3Properties.getProxyPort())
          .map(Integer::parseInt)
          .ifPresent(clientConfiguration::setProxyPort);
    }

    AmazonS3Client client = new AmazonS3Client(awsCredentialsProvider, clientConfiguration);

    if (!StringUtils.isEmpty(s3Properties.getEndpoint())) {
      client.setEndpoint(s3Properties.getEndpoint());

      if (!StringUtils.isEmpty(s3Properties.getRegionOverride())) {
        client.setSignerRegionOverride(s3Properties.getRegionOverride());
      }

      client.setS3ClientOptions(
          S3ClientOptions.builder().setPathStyleAccess(s3Properties.getPathStyleAccess()).build());
    } else {
      Optional.ofNullable(s3Properties.getRegion())
          .map(Regions::fromName)
          .map(Region::getRegion)
          .ifPresent(client::setRegion);
    }

    return client;
  }
}
