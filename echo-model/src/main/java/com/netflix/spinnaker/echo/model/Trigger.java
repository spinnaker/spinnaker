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

package com.netflix.spinnaker.echo.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Wither;

import java.util.List;
import java.util.Map;

@JsonDeserialize(builder = Trigger.TriggerBuilder.class)
@Builder(toBuilder = true)
@Wither
@ToString(of = {"type", "master", "job", "cronExpression", "source", "project", "slug", "account", "repository", "tag", "constraints", "branch", "runAsUser", "subscriptionName", "pubsubSystem", "expectedArtifacts"}, includeFieldNames = false)
@Value
public class Trigger {
  public enum Type {
    CRON("cron"),
    GIT("git"),
    JENKINS("jenkins"),
    DOCKER("docker"),
    WEBHOOK("webhook"),
    PUBSUB("pubsub");

    private final String type;

    Type(String type) {
      this.type = type;
    }

    @Override
    public String toString() {
      return type;
    }
  }

  boolean enabled;
  String id;
  String type;
  String master;
  String job;
  Integer buildNumber;
  String propertyFile;
  String cronExpression;
  String source;
  String project;
  String slug;
  String hash;
  String account;
  String repository;
  String tag;
  String digest;
  Map constraints;
  String branch;
  String runAsUser;
  String secret;
  String subscriptionName;
  String pubsubSystem;
  List<ExpectedArtifact> expectedArtifacts;

  public Trigger atBuildNumber(final int buildNumber) {
    return this.toBuilder()
        .buildNumber(buildNumber)
        .hash(null)
        .tag(null)
        .constraints(null)
        .subscriptionName(null)
        .pubsubSystem(null)
        .build();
  }

  public Trigger atHash(final String hash) {
    return this.toBuilder()
        .buildNumber(null)
        .hash(hash)
        .tag(null)
        .constraints(null)
        .subscriptionName(null)
        .pubsubSystem(null)
        .build();
  }

  public Trigger atBranch(final String branch) {
    return this.toBuilder()
        .buildNumber(null)
        .tag(null)
        .constraints(null)
        .branch(branch)
        .subscriptionName(null)
        .pubsubSystem(null)
        .build();
  }

  public Trigger atTag(final String tag) {
    return this.toBuilder()
        .buildNumber(null)
        .hash(null)
        .tag(tag)
        .constraints(null)
        .subscriptionName(null)
        .pubsubSystem(null)
        .build();
  }

  public Trigger atConstraints(final Map constraints) {
    return this.toBuilder()
        .buildNumber(null)
        .hash(null)
        .digest(null)
        .constraints(constraints)
        .subscriptionName(null)
        .pubsubSystem(null)
        .build();
  }

  public Trigger atSecret(final String secret) {
    return this.toBuilder()
        .buildNumber(null)
        .hash(null)
        .digest(null)
        .secret(secret)
        .subscriptionName(null)
        .pubsubSystem(null)
        .build();
  }

  public Trigger atMessageDescription(final String subscriptionName, final String pubsubSystem) {
    return this.toBuilder()
        .subscriptionName(subscriptionName)
        .pubsubSystem(pubsubSystem)
        .build();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static final class TriggerBuilder {
  }
}
