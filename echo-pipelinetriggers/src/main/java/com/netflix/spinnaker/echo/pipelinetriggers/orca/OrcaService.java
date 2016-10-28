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

package com.netflix.spinnaker.echo.pipelinetriggers.orca;

import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import retrofit.http.Body;
import retrofit.http.Header;
import retrofit.http.POST;
import rx.Observable;

public interface OrcaService {
  @POST("/orchestrate")
  Observable<Response> trigger(@Body Pipeline pipeline);

  @POST("/orchestrate")
  Observable<Response> trigger(@Body Pipeline pipeline, @Header(AuthenticatedRequest.SPINNAKER_USER) String runAsUser);

  class Response {
    private String ref;

    public Response() {
      // do nothing
    }

    public String getRef() {
      return ref;
    }
  }
}
