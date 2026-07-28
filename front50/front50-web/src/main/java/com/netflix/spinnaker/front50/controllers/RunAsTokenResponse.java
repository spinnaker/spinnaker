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
 * Response from the run-as token mint/exchange endpoint: a signed identity token plus the resolved
 * subject and roles it carries. Long-running executions re-call the endpoint at each stage boundary
 * to obtain a fresh, bounded-lifetime token (re-resolving roles each time).
 */
public class RunAsTokenResponse {
  private final String token;
  private final String subject;
  private final List<String> roles;

  public RunAsTokenResponse(String token, String subject, List<String> roles) {
    this.token = token;
    this.subject = subject;
    this.roles = roles;
  }

  public String getToken() {
    return token;
  }

  public String getSubject() {
    return subject;
  }

  public List<String> getRoles() {
    return roles;
  }
}
