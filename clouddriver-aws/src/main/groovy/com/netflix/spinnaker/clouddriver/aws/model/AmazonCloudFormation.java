/*
 * Copyright (c) 2019 Schibsted Media Group.
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
package com.netflix.spinnaker.clouddriver.aws.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.netflix.spinnaker.clouddriver.model.CloudFormation;

import java.util.Date;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AmazonCloudFormation implements CloudFormation {
  final String type = "aws";
  private String stackId;
  private Map<String, String> tags;
  private Map<String, String> outputs;
  private String stackName;
  private String region;
  private String stackStatus;
  private String stackStatusReason;
  private String accountName;
  private String accountId;
  private Date creationTime;

  @Override
  public String getStackId() {
    return stackId;
  }

  @Override
  public Map<String, String> getTags() {
    return tags;
  }

  @Override
  public Map<String, String> getOutputs() {
    return outputs;
  }

  @Override
  public String getStackName() {
    return stackName;
  }

  @Override
  public String getRegion() {
    return region;
  }

  @Override
  public String getAccountName() {
    return accountName;
  }

  @Override
  public String getAccountId() {
    return accountId;
  }

  @Override
  public String getStackStatus() {
    return stackStatus;
  }

  @Override
  public String getStackStatusReason() {
    return stackStatusReason;
  }

  @Override
  public Date getCreationTime() {
    return creationTime;
  }

  @Override
  public boolean equals(Object cf) {
    if (cf instanceof AmazonCloudFormation) {
      return stackId.equals(((AmazonCloudFormation) cf).stackId);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return stackId.hashCode();
  }
}
