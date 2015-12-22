/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.security;

import com.google.api.services.compute.Compute;

import java.util.List;

public class GoogleCredentials {
  private final String project;
  private final Compute compute;
  private final List<String> imageProjects;

  public GoogleCredentials(String project, Compute compute) {
    this(project, compute, null);
  }

  public GoogleCredentials(String project, Compute compute, List<String> imageProjects) {
    this.project = project;
    this.compute = compute;
    this.imageProjects = imageProjects;
  }

  public String getProject() {
    return project;
  }

  public Compute getCompute() {
    return compute;
  }

  public List<String> getImageProjects() {
    return imageProjects;
  }
}
