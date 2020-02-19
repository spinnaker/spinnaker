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

import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.front50.model.S3StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "spinnaker.s3.storage-service.enabled", matchIfMissing = true)
public class S3StorageServiceConfiguration {

  @Bean
  public S3StorageService s3StorageService(AmazonS3 amazonS3, S3Properties s3Properties) {
    ObjectMapper awsObjectMapper = new ObjectMapper();

    S3StorageService service =
        new S3StorageService(
            awsObjectMapper,
            amazonS3,
            s3Properties.getBucket(),
            s3Properties.getRootFolder(),
            s3Properties.isFailoverEnabled(),
            s3Properties.getRegion(),
            s3Properties.getVersioning(),
            s3Properties.getMaxKeys(),
            s3Properties.getServerSideEncryption());
    service.ensureBucketExists();

    return service;
  }
}
