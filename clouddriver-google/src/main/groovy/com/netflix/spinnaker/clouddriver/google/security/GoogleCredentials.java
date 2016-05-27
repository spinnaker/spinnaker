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
  // TODO(ttomsu): Remove this as part of credentials refactoring.
  private final boolean alphaListed;
  private final List<String> imageProjects;
  private final List<String> requiredGroupMembership;
  private final String accountName;

  public GoogleCredentials(String project, Compute compute) {
    this(project, compute, false, null, null, null);
  }

  public GoogleCredentials(String project, Compute compute, boolean alphaListed, List<String> imageProjects, List<String> requiredGroupMembership, String accountName) {
    this.project = project;
    this.compute = compute;
    this.alphaListed = alphaListed;
    this.imageProjects = imageProjects;
    this.requiredGroupMembership = requiredGroupMembership;
    this.accountName = accountName;
  }

  public String getProject() {
    return project;
  }

  public Compute getCompute() {
    return compute;
  }

  public boolean getAlphaListed() {
    return alphaListed;
  }

  public List<String> getImageProjects() {
    return imageProjects;
  }

  public List<String> getRequiredGroupMembership() {
    return requiredGroupMembership;
  }

  // TODO(ttomsu): This is a temporary hack (famous last words) to get requiredGroupMembership validation to work.
  // Make this cleaner by switching GCE objects to use GoogleNamedAccountCredentials instead of this class.
  public String getName() {
    return accountName;
  }
}
