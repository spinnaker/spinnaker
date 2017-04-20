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

package com.netflix.spinnaker.orca.pipeline.model;

import com.netflix.spinnaker.orca.ExecutionStatus;
import lombok.Data;
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;

/**
 * A "task" is a component piece of a stage
 */
@Data
public class Task {
  String id;
  String implementingClass;
  String name;
  Long startTime;
  Long endTime;
  ExecutionStatus status = NOT_STARTED;
  boolean stageStart;
  boolean stageEnd;
  boolean loopStart;
  boolean loopEnd;
}
