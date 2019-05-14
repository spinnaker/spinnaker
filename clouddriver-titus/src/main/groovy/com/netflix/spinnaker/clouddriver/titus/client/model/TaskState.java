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

public enum TaskState {
  ALL,
  RUNNING,
  DISPATCHED,
  FAILED,
  STOPPED,
  CRASHED,
  FINISHED,
  STARTING,
  QUEUED,
  TERMINATING,
  DEAD,
  PENDING; // Deprecated

  public static TaskState from(String taskStateStr) {
    for (TaskState taskState : TaskState.values()) {
      if (taskState.name().equals(taskStateStr)) return taskState;
    }
    switch (taskStateStr) {
      case "Accepted":
        return TaskState.QUEUED;
      case "Launched":
        return TaskState.DISPATCHED;
      case "StartInitiated":
        return TaskState.STARTING;
      case "Started":
        return TaskState.RUNNING;
      case "KillInitiated":
      case "Disconnected":
      case "Finished":
        return TaskState.FINISHED;
      default:
        return null;
    }
  }

  public static TaskState from(String taskStateStr, String reasonCode) {

    if (taskStateStr.equals("Finished")) {
      switch (reasonCode) {
        case "normal":
          return TaskState.FINISHED;
        case "killed":
          return TaskState.STOPPED;
        case "crashed":
        case "lost":
          return TaskState.CRASHED;
        case "failed":
          return TaskState.FAILED;
        default:
          return TaskState.FINISHED;
      }
    }

    switch (taskStateStr) {
      case "Accepted":
        return TaskState.QUEUED;
      case "Launched":
        return TaskState.DISPATCHED;
      case "StartInitiated":
        return TaskState.STARTING;
      case "Started":
        return TaskState.RUNNING;
      case "KillInitiated":
      case "Disconnected":
        return TaskState.FINISHED;
      default:
        return null;
    }
  }
}
