/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.clouddriver.safety;

public class ClusterMatchRule {
  private String account;
  private String location;
  private String stack;
  private String detail;
  private Integer priority;

  public ClusterMatchRule() {}

  public ClusterMatchRule(
      String account, String location, String stack, String detail, Integer priority) {
    this.account = account;
    this.location = location;
    this.stack = stack;
    this.detail = detail;
    this.priority = priority;
  }

  public String getAccount() {
    return account == null ? "" : account;
  }

  public void setAccount(String account) {
    this.account = account;
  }

  public String getLocation() {
    return location == null ? "" : location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getStack() {
    return stack == null ? "" : stack;
  }

  public void setStack(String stack) {
    this.stack = stack;
  }

  public String getDetail() {
    return detail == null ? "" : detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public Integer getPriority() {
    return priority == null ? 0 : priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority;
  }
}
