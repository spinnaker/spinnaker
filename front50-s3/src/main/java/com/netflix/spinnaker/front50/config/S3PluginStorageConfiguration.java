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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.netflix.spinnaker.front50.plugins.PluginBinaryStorageService;
import com.netflix.spinnaker.front50.plugins.S3PluginBinaryStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("spinnaker.s3.plugin-storage.enabled")
public class S3PluginStorageConfiguration {

  @Bean
  public AmazonS3 awsS3PluginClient(
      AWSCredentialsProvider awsCredentialsProvider, S3PluginStorageProperties s3Properties) {
    return S3ClientFactory.create(awsCredentialsProvider, s3Properties);
  }

  @Bean
  PluginBinaryStorageService pluginBinaryStorageService(
      AmazonS3 awsS3PluginClient, S3PluginStorageProperties properties) {
    return new S3PluginBinaryStorageService(awsS3PluginClient, properties);
  }
}
