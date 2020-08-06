/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.front50.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spinnaker.gcs")
public class GcsProperties {
  private String bucket;

  private String bucketLocation;

  private String rootFolder = "front50";

  private String jsonPath = "";

  private String project = "";

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    // "google.com:" is deprecated but may be in certain old projects.
    if (bucket.startsWith("google.com:")) {
      bucket = bucket.substring("google.com:".length());
    }
    this.bucket = bucket;
  }

  public String getBucketLocation() {
    return bucketLocation;
  }

  public void setBucketLocation(String bucketLocation) {
    this.bucketLocation = bucketLocation;
  }

  public String getRootFolder() {
    return rootFolder;
  }

  public void setRootFolder(String rootFolder) {
    this.rootFolder = rootFolder;
  }

  public String getJsonPath() {
    return jsonPath;
  }

  public void setJsonPath(String jsonPath) {
    this.jsonPath = jsonPath;
  }

  public String getProject() {
    return project;
  }

  public void setProject(String project) {
    this.project = project;
  }
}
