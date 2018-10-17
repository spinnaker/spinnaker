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

import retrofit.http.Body;
import retrofit.http.Header;
import retrofit.http.POST;
import retrofit.http.Query;

/**
 * Retrofit interface for SignalFx API calls.
 */
public interface SignalFxSignalFlowRemoteService {

  /**
   * Executes a signal flow program.
   *
   * @param accessToken     The SignalFx API Access token associated with the organization that you are querying.
   * @param startEpochMilli (Optional) start timestamp in milliseconds since epoch
   * @param endEpochMilli   (Optional) stop timestamp in milliseconds since epoch
   * @param resolution      (Optional) the minimum desired data resolution, in milliseconds
   * @param maxDelay        (Optional) desired maximum data delay, in milliseconds between 0 (for automatic maximum delay) and 900000
   * @param immediate       (Optional) whether to adjust the stop timestamp so that the computation doesn't wait for future data to be available
   * @param program         The signal flow program to execute
   * @return The list of channel messages from the signal flow output
   */
  @POST("/v2/signalflow/execute")
  SignalFlowExecutionResult executeSignalFlowProgram(@Header("X-SF-TOKEN") String accessToken,
                                                     @Query("start") long startEpochMilli,
                                                     @Query("stop") long endEpochMilli,
                                                     @Query("resolution") long resolution,
                                                     @Query("maxDelay") long maxDelay,
                                                     @Query("immediate") boolean immediate,
                                                     @Body String program);
}
