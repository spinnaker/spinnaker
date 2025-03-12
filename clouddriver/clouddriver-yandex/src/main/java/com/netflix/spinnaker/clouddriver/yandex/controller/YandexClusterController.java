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

import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexLogRecord;
import com.netflix.spinnaker.clouddriver.yandex.provider.view.YandexClusterProvider;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
    "/applications/{application}/clusters/{account}/{clusterName}/yandex/serverGroups/{serverGroupName}")
public class YandexClusterController {
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final YandexClusterProvider yandexClusterProvider;
  private final YandexCloudFacade yandexCloudFacade;

  @Autowired
  public YandexClusterController(
      AccountCredentialsProvider accountCredentialsProvider,
      YandexClusterProvider yandexClusterProvider,
      YandexCloudFacade yandexCloudFacade) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.yandexClusterProvider = yandexClusterProvider;
    this.yandexCloudFacade = yandexCloudFacade;
  }

  @RequestMapping(value = "/scalingActivities", method = RequestMethod.GET)
  public ResponseEntity<List<Activity>> getScalingActivities(
      @PathVariable String account,
      @PathVariable String serverGroupName,
      @RequestParam(value = "region") String region) {
    AccountCredentials<?> credentials = accountCredentialsProvider.getCredentials(account);
    if (!(credentials instanceof YandexCloudCredentials)) {
      return ResponseEntity.badRequest().build();
    }

    YandexCloudServerGroup serverGroup =
        yandexClusterProvider.getServerGroup(account, region, serverGroupName);
    if (serverGroup == null) {
      return ResponseEntity.notFound().build();
    }

    List<YandexLogRecord> yandexLogRecordList =
        yandexCloudFacade.getLogRecords((YandexCloudCredentials) credentials, serverGroup.getId());
    return ResponseEntity.ok(
        yandexLogRecordList.stream().map(this::getActivity).collect(Collectors.toList()));
  }

  @NotNull
  private YandexClusterController.Activity getActivity(YandexLogRecord record) {
    return new Activity(
        "details",
        record.getMessage(),
        "cause: " + record.getMessage(),
        "Successful",
        record.getTimstamp());
  }

  @Value
  public static class Activity {
    String details;
    String description;
    String cause;
    String statusCode;
    Instant startTime;
  }
}
