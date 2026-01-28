/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.cats.redis.cluster;

/**
 * Exception thrown when agent scheduling operations fail.
 *
 * <p>This exception is used by PriorityAgentScheduler to provide more specific error information
 * than generic RuntimeException, making it easier for calling code to handle scheduling-specific
 * failures appropriately.
 *
 * <p>Common scenarios where this exception is thrown:
 *
 * <ul>
 *   <li>Redis connection failures during agent scheduling
 *   <li>Lua script execution failures
 *   <li>Agent state transition failures
 *   <li>Critical scheduler configuration errors
 * </ul>
 */
public class AgentSchedulingException extends RuntimeException {

  /**
   * Create exception with descriptive message.
   *
   * @param message Description of the scheduling failure
   */
  public AgentSchedulingException(String message) {
    super(message);
  }

  /**
   * Create exception with message and underlying cause.
   *
   * @param message Description of the scheduling failure
   * @param cause The underlying exception that caused the scheduling failure
   */
  public AgentSchedulingException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Create exception wrapping an existing throwable.
   *
   * @param cause The underlying exception that caused the scheduling failure
   */
  public AgentSchedulingException(Throwable cause) {
    super(cause);
  }
}
