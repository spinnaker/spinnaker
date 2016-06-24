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
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Wither;
import java.util.Map;

@JsonDeserialize(builder = Trigger.TriggerBuilder.class)
@Builder
@Wither
@ToString(of = {"type", "master", "job", "cronExpression", "source", "project", "slug", "account", "repository", "tag", "constraints", "branch"}, includeFieldNames = false)
@Value
public class Trigger {
  public enum Type {
    CRON("cron"),
    GIT("git"),
    JENKINS("jenkins"),
    DOCKER("docker"),
    WEBHOOK("webhook");

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

  public Trigger atBuildNumber(final int buildNumber) {
    return new Trigger(enabled, id, type, master, job, buildNumber, propertyFile, cronExpression, source, project, slug, null, account, repository, null, digest, null, branch);
  }

  public Trigger atHash(final String hash) {
    return new Trigger(enabled, id, type, master, job, null, propertyFile, cronExpression, source, project, slug, hash, account, repository, null, digest, null, branch);
  }

  public Trigger atBranch(final String branch) {
    return new Trigger(enabled, id, type, master, job, null, propertyFile, cronExpression, source, project, slug, hash, account, repository, null, digest, null, branch);
  }

  public Trigger atTag(final String tag) {
    return new Trigger(enabled, id, type, master, job, null, propertyFile, cronExpression, source, project, slug, null, account, repository, tag, digest, null, branch);
  }

  public Trigger atConstraints(final Map constraints) {
    return new Trigger(enabled, id, type, master, job, null, propertyFile, cronExpression, source, project, slug, null, account, repository, tag, null, constraints, branch);
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static final class TriggerBuilder {
  }
}
