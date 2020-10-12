/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudImage;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServiceAccount;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexLogRecord;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import com.netflix.spinnaker.config.YandexCloudConfiguration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import yandex.cloud.api.compute.v1.instancegroup.InstanceGroupOuterClass;

@Configuration
@Import(value = {YandexCloudConfiguration.class})
class TestConfig {
  public static final String TEST_SA_ID = "test-sa-id";
  public static final String ACCOUNT = "test-account";
  public static final String SA_NAME = "sa-name";
  public static final String FOLDER_ID = "folder-id";
  public static final String IMAGE_NAME = "ubuntu";
  public static final String IMAGE_ID = "image-id";
  public static final String SERVER_GROUP_NAME = "test-server-group";
  public static final String ACTIVITY = "done";
  public static final String SERVER_GROUP_ID = "server-group-id";

  @Bean
  @Primary
  public List<YandexCloudCredentials> yandexCloudCredentials(
      AccountCredentialsRepository repository) {
    YandexCloudCredentials mock = mock(YandexCloudCredentials.class);
    when(mock.getName()).thenReturn(ACCOUNT);
    when(mock.getFolder()).thenReturn(FOLDER_ID);
    repository.save(ACCOUNT, mock);
    return Collections.singletonList(mock);
  }

  @Bean
  @Primary
  public YandexCloudFacade yandexCloudFacade() {
    YandexCloudFacade mock = mock(YandexCloudFacade.class);
    when(mock.getServiceAccounts(any(), eq(FOLDER_ID))).thenReturn(testServiceAccounts());
    when(mock.getImages(any(), eq(FOLDER_ID))).thenReturn(testImages());
    when(mock.getServerGroups(any())).thenReturn(testGroups());
    when(mock.getLogRecords(any(), eq(SERVER_GROUP_ID))).thenReturn(testLogRecords());
    return mock;
  }

  private static List<YandexCloudImage> testImages() {
    YandexCloudImage image =
        new YandexCloudImage(
            IMAGE_ID,
            IMAGE_NAME,
            "desc",
            YandexCloudProvider.REGION,
            System.currentTimeMillis(),
            Collections.singletonMap("key", "value"));
    return Collections.singletonList(image);
  }

  private static List<YandexCloudServiceAccount> testServiceAccounts() {
    YandexCloudServiceAccount sa = new YandexCloudServiceAccount(TEST_SA_ID, SA_NAME, ACCOUNT);
    return Collections.singletonList(sa);
  }

  private static List<InstanceGroupOuterClass.InstanceGroup> testGroups() {
    InstanceGroupOuterClass.InstanceGroup ig =
        InstanceGroupOuterClass.InstanceGroup.newBuilder()
            .setId(SERVER_GROUP_ID)
            .setName(SERVER_GROUP_NAME)
            .setFolderId(FOLDER_ID)
            .build();
    return Collections.singletonList(ig);
  }

  private static List<YandexLogRecord> testLogRecords() {
    YandexLogRecord record1 = new YandexLogRecord(Instant.now(), "init");
    YandexLogRecord record2 = new YandexLogRecord(Instant.now(), ACTIVITY);
    return Arrays.asList(record1, record2);
  }
}
