/*
 * Copyright 2022 Armory
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

package com.netflix.spinnaker.echo.scm.bitbucket.server;

import lombok.Data;

@Data
public class BitbucketServerPrEvent {

  private PullRequest pullRequest;

  @Data
  public static class PullRequest {
    BitbucketServerPrEvent.Ref fromRef;
    BitbucketServerPrEvent.Ref toRef;
    BitbucketServerPrEvent.Properties properties;
  }

  @Data
  public static class Ref {
    String id;
    String latestCommit;
    BitbucketServerPrEvent.Repository repository;
  }

  @Data
  public static class Project {
    String key;
  }

  @Data
  public static class Repository {
    String name;
    String slug;
    Project project;
  }

  @Data
  public static class Properties {
    BitbucketServerPrEvent.MergeCommit mergeCommit;
  }

  @Data
  public static class MergeCommit {
    String id;
  }
}
