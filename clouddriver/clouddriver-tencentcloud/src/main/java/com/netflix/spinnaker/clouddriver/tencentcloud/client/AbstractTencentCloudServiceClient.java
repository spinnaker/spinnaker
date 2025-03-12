/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.clouddriver.tencentcloud.client;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractTencentCloudServiceClient {
  public static final long MAX_QUERY_TIME = 1000;
  public static final long DEFAULT_LIMIT = 100;
  private Credential credential;
  private HttpProfile httpProfile;
  private ClientProfile clientProfile;

  public abstract String getEndPoint();

  public AbstractTencentCloudServiceClient(String secretId, String secretKey) {
    credential = new Credential(secretId, secretKey);
    httpProfile = new HttpProfile();
    httpProfile.setEndpoint(getEndPoint());
    clientProfile = new ClientProfile();
    clientProfile.setHttpProfile(httpProfile);
  }

  public static Date convertToIsoDateTime(String isoDateTime) {
    try {
      DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
      TemporalAccessor accessor = timeFormatter.parse(isoDateTime);
      return Date.from(Instant.from(accessor));
    } catch (Exception e) {
      log.warn("convert time error " + e.toString());
      return null;
    }
  }

  public Credential getCredential() {
    return credential;
  }

  public void setCredential(Credential credential) {
    this.credential = credential;
  }

  public HttpProfile getHttpProfile() {
    return httpProfile;
  }

  public void setHttpProfile(HttpProfile httpProfile) {
    this.httpProfile = httpProfile;
  }

  public ClientProfile getClientProfile() {
    return clientProfile;
  }

  public void setClientProfile(ClientProfile clientProfile) {
    this.clientProfile = clientProfile;
  }
}
