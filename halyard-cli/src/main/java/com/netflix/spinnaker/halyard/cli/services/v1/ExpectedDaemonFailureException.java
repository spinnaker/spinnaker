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

package com.netflix.spinnaker.halyard.cli.services.v1;

/**
 * This is used for the CLI to indicate that the Daemon failed to process a request in an expected
 * way.
 *
 * <p>Examples include: An account couldn't be added because of a duplicate name.
 *
 * <p>Non-examples include: The CLI encountered an NPE.
 */
public class ExpectedDaemonFailureException extends RuntimeException {
  ExpectedDaemonFailureException(Throwable cause) {
    super(cause);
  }

  ExpectedDaemonFailureException(String msg) {
    super(msg);
  }

  ExpectedDaemonFailureException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
