/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.core.tasks.v1;

import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DaemonTaskInterrupted extends RuntimeException {
  Long interruptedTime;
  String message;

  public DaemonTaskInterrupted() {
    interruptedTime = System.currentTimeMillis();
    this.message = "Task interrupted without cause at " + new Date(interruptedTime);
  }

  public DaemonTaskInterrupted(Exception e) {
    super(e);
    this.interruptedTime = System.currentTimeMillis();
    this.message = "Task interrupted at " + new Date(interruptedTime) + ": " + e.getMessage();
  }

  public DaemonTaskInterrupted(String message) {
    this.interruptedTime = System.currentTimeMillis();
    this.message = "Task interrupted at " + new Date(interruptedTime) + " with message: " + message;
  }

  public DaemonTaskInterrupted(String message, Exception e) {
    super(e);
    this.interruptedTime = System.currentTimeMillis();
    this.message =
        "Task interrupted at "
            + new Date(interruptedTime)
            + " with message: "
            + message
            + " by exception: "
            + e.getMessage();
  }
}
