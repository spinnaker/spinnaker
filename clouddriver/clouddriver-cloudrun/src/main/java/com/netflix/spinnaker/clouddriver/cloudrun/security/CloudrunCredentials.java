/*
 * Copyright 2022 OpsMx Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.security;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.run.v1.CloudRun;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.netflix.spinnaker.clouddriver.googlecommon.security.GoogleCommonCredentials;

public class CloudrunCredentials extends GoogleCommonCredentials {

  private final String project;

  public CloudrunCredentials(String project) {
    this.project = project;
  }

  public CloudRun getCloudrun(String applicationName) {
    HttpTransport httpTransport = buildHttpTransport();
    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    GoogleCredentials credentials =
        getCredentials().createScoped("https://www.googleapis.com/auth/cloud-platform");
    HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);
    return new CloudRun.Builder(httpTransport, jsonFactory, requestInitializer)
        .setApplicationName(applicationName)
        .build();
  }
}
