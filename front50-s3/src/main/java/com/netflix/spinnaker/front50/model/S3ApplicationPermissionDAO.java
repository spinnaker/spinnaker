/*
 * Copyright 2016 Google, Inc.
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


package com.netflix.spinnaker.front50.model;

import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.front50.model.application.Application;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import rx.Scheduler;

public class S3ApplicationPermissionDAO extends S3Support<Application.Permission> implements ApplicationPermissionDAO {
  public S3ApplicationPermissionDAO(ObjectMapper objectMapper,
                                    AmazonS3 amazonS3,
                                    Scheduler scheduler,
                                    int refreshIntervalMs,
                                    String bucket,
                                    String rootFolder) {
    super(objectMapper, amazonS3, scheduler, refreshIntervalMs, bucket, (rootFolder + "/applications/").replaceAll("//", "/"));
  }

  @Override
  public Application.Permission create(String id, Application.Permission permission) {
    return upsert(id, permission);
  }

  @Override
  public void update(String id, Application.Permission permission) {
    upsert(id, permission);
  }

  private Application.Permission upsert(String id, Application.Permission permission) {
    permission.setName(id);
    super.update(id, permission);
    return findById(id);
  }

  @Override
  Class<Application.Permission> getSerializedClass() {
    return Application.Permission.class;
  }

  @Override
  String getMetadataFilename() {
    return "application-permission.json";
  }
}
