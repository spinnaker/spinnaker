/*
 * Copyright 2026 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.data.task;

/**
 * Thrown when a write to a task is rejected because the executing instance no longer owns
 * the task's lease: the instance was declared dead by the reaper after its lease expired,
 * or its registration epoch was superseded after a restart. The executor should abandon
 * the task — the instance that owns the lease (or the reaper) decides its outcome, and a
 * non-idempotent operation is never re-run blindly.
 */
public class TaskLeaseLostException extends RuntimeException {
  public TaskLeaseLostException(String message) {
    super(message);
  }
}
