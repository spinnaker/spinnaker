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
 */

package com.netflix.kayenta.s3.config;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer;
import com.netflix.kayenta.aws.security.AwsNamedAccountCredentials;
import com.netflix.kayenta.s3.storage.S3StorageService;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
@ConditionalOnProperty("kayenta.s3.enabled")
@ComponentScan({"com.netflix.kayenta.s3"})
@Slf4j
public class S3Configuration {

  @Autowired
  ObjectMapper kayentaObjectMapper;

  @Bean
  @DependsOn({"registerAwsCredentials"})
  public S3StorageService s3StorageService(AccountCredentialsRepository accountCredentialsRepository) {
    AmazonObjectMapperConfigurer.configure(kayentaObjectMapper);
    kayentaObjectMapper.configure(MapperFeature.AUTO_DETECT_IS_GETTERS, true);
    S3StorageService.S3StorageServiceBuilder s3StorageServiceBuilder = S3StorageService.builder();

    accountCredentialsRepository
      .getAll()
      .stream()
      .filter(c -> c instanceof AwsNamedAccountCredentials)
      .filter(c -> c.getSupportedTypes().contains(AccountCredentials.Type.OBJECT_STORE))
      .map(c -> c.getName())
      .forEach(s3StorageServiceBuilder::accountName);

    S3StorageService s3StorageService = s3StorageServiceBuilder.objectMapper(kayentaObjectMapper).build();

    log.info("Populated S3StorageService with {} AWS accounts.", s3StorageService.getAccountNames().size());

    return s3StorageService;
  }
}
