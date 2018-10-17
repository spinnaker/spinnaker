/*
 * Copyright (c) 2018 Nike, inc.
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

package com.netflix.kayenta.signalfx.service;

public class SignalFxRequestError extends RuntimeException {

  private static final String MSG_TEMPLATE =
      "An error occurred when trying to execute a SignalFlow program " +
          "with program='%s', startMs='%s', endMs='%s', resolution='%s' for accountName: %s. Received error response: %s";

  public SignalFxRequestError(ErrorResponse errorResponse, String program, long start, long end,
                              long resolution, String accountName) {

    super(String.format(MSG_TEMPLATE, program, start, end, resolution, accountName, errorResponse.toString()));
  }
}
