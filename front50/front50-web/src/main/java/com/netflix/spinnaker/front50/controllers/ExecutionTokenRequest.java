/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.front50.controllers;

import java.util.List;

/**
 * Request body for {@code POST /auth/issueExecutionToken}. Orca posts an in-flight execution's
 * already-admitted subject and roles so Front50 can re-issue a fresh identity token across an async
 * stage boundary.
 *
 * <p>The request is unsigned: authorization is the service-to-service caller identity (Front50
 * requires the authenticated caller to be Orca via {@code authz.s2s}), so no signing key is
 * involved. The subject/roles are trusted because they arrive from Orca over an authenticated
 * channel; a service-account subject with no roles has them re-resolved from Front50's own store.
 */
public class ExecutionTokenRequest {
  private String subject;
  private List<String> roles;
  private boolean admin;
  private boolean accountManager;

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(List<String> roles) {
    this.roles = roles;
  }

  public boolean isAdmin() {
    return admin;
  }

  public void setAdmin(boolean admin) {
    this.admin = admin;
  }

  public boolean isAccountManager() {
    return accountManager;
  }

  public void setAccountManager(boolean accountManager) {
    this.accountManager = accountManager;
  }
}
