/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.orca.tide.model

import groovy.transform.Immutable

class TideTask {
  String taskId
  Description taskDescription
  List<History> history
  List warnings
  List<Mutation> mutations
  TaskComplete taskComplete

  @Immutable
  static class History {
    String taskId
    String message
    Long timeStamp
  }

  @Immutable
  static class Description {
    DescriptionSource source
    DescriptionTarget target
    boolean dryRun
    String taskType
  }

  @Immutable
  static class DescriptionSource {
    Map location
    Map identity
  }

  @Immutable
  static class DescriptionTarget {
    String account
    String region
    String vpcName
  }

  @Immutable
  static class TaskComplete {
    String taskId
    Description description
    Map result
    String message
  }

  @Immutable
  static class Mutation {
    String taskId
    Map AwsReference
    String operation
  }
}
