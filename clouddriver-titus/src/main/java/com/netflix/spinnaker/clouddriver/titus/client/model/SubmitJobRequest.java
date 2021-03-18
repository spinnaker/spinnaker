/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.client.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.experimental.Wither;

@JsonDeserialize(builder = SubmitJobRequest.SubmitJobRequestBuilder.class)
@Builder(builderClassName = "SubmitJobRequestBuilder", toBuilder = true)
@Wither
@Value
public class SubmitJobRequest {

  @JsonDeserialize(builder = Constraint.ConstraintBuilder.class)
  @Builder(builderClassName = "ConstraintBuilder", toBuilder = true)
  @Value
  public static class Constraint {
    enum ConstraintType {
      SOFT,
      HARD
    }

    public static final String UNIQUE_HOST = "UniqueHost";
    public static final String ZONE_BALANCE = "ZoneBalance";

    public static Constraint hard(String constraint) {
      return new Constraint(ConstraintType.HARD, constraint);
    }

    public static Constraint soft(String constraint) {
      return new Constraint(ConstraintType.SOFT, constraint);
    }

    @JsonProperty private final ConstraintType constraintType;
    @JsonProperty private final String constraint;

    @JsonPOJOBuilder(withPrefix = "")
    public static class ConstraintBuilder {}
  }

  @Data
  public static class Constraints {
    public Map hard;
    public Map soft;
  }

  private String credentials;
  private String jobType;
  private String application;
  private String jobName;
  private String dockerImageName;
  private String dockerImageVersion;
  private String dockerDigest;
  private String stack;
  private String detail;
  private String user;
  private String entryPoint;
  private String cmd;
  private String iamProfile;
  private String capacityGroup;
  @Builder.Default private Boolean inService = true;
  private int instancesMin;
  private int instancesMax;
  private int instancesDesired;
  private int cpu;
  private int gpu;
  private int memory;
  private int sharedMemory;
  private int disk;
  private int retries;
  private int runtimeLimitSecs;
  private int networkMbps;
  private Efs efs;
  private int[] ports;
  private Map<String, String> env;
  private boolean allocateIpAddress;
  @Builder.Default private List<Constraint> constraints = new ArrayList<>();
  @Builder.Default private Map<String, String> labels = new HashMap<String, String>();
  @Builder.Default private Map<String, String> containerAttributes = new HashMap<String, String>();
  @Builder.Default private List<String> securityGroups = null;
  @Builder.Default private MigrationPolicy migrationPolicy = null;
  @Builder.Default private DisruptionBudget disruptionBudget = null;

  @Builder.Default private Constraints containerConstraints = null;
  @Builder.Default private ServiceJobProcesses serviceJobProcesses = null;
  @Builder.Default private List<SignedAddressAllocations> signedAddressAllocations = null;

  @JsonIgnore
  public JobDescription getJobDescription() {
    return new JobDescription(this);
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class SubmitJobRequestBuilder {}
}
