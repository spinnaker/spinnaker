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


package com.netflix.spinnaker.orca.retrofit.exceptions

import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import retrofit.RetrofitError
import retrofit.mime.TypedByteArray

class RetrofitExceptionHandler implements ExceptionHandler<RetrofitError> {
  @Override
  boolean handles(RuntimeException e) {
    return e.class == RetrofitError
  }

  @Override
  ExceptionHandler.Response handle(String stepName, RetrofitError e) {
    e.getResponse().with {
      def response = new ExceptionHandler.Response(exceptionType: e.class.simpleName, operation: stepName)

      try {
        def body = e.getBodyAs(Map) as Map
        response.details = new ExceptionHandler.ResponseDetails(
          body.error ?: reason,
          (body.errors ?: (body.message ? [body.message] : [])) as List<String>
        )

        if (body.exception) {
          response.details.rootException = body.exception
        }
      } catch (ignored) {
        response.details = new ExceptionHandler.ResponseDetails(reason)
      }

      try {
        response.details.responseBody = new String(((TypedByteArray)e.getResponse().getBody()).getBytes())
      } catch (ignored) {
        response.details.responseBody = "n/a"
      }
      response.details.status = status
      response.details.url = url
      return response
    }
  }
}
