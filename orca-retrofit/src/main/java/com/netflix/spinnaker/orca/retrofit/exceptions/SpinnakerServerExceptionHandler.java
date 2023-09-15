/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.retrofit.exceptions;

import com.google.common.base.Throwables;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpinnakerServerExceptionHandler extends BaseRetrofitExceptionHandler {

  private final Logger log = LoggerFactory.getLogger(SpinnakerServerExceptionHandler.class);

  @Override
  public boolean handles(Exception e) {
    return e instanceof SpinnakerServerException;
  }

  @Override
  public Response handle(String taskName, Exception exception) {
    SpinnakerServerException ex = (SpinnakerServerException) exception;

    Map<String, Object> responseDetails;

    String kind;
    Integer responseCode = null;

    if (ex instanceof SpinnakerNetworkException) {
      kind = "NETWORK";
      responseDetails = ExceptionHandler.responseDetails(ex.getMessage());
      responseDetails.put("stackTrace", Throwables.getStackTraceAsString(ex));
    } else if (ex instanceof SpinnakerHttpException) {
      SpinnakerHttpException spinnakerHttpException = (SpinnakerHttpException) ex;
      kind = "HTTP";
      responseCode = spinnakerHttpException.getResponseCode();

      Map<String, Object> body = spinnakerHttpException.getResponseBody();

      // It's not only non-json responses that have null response bodies.  It's
      // possible that other responses have null bodies too, according to
      // https://github.com/square/retrofit/blob/parent-1.9.0/retrofit/src/main/java/retrofit/client/Response.java#L78,
      // so handle it.
      if (body == null) {
        responseDetails = ExceptionHandler.responseDetails(spinnakerHttpException.getMessage());
      } else {
        String error = (String) body.getOrDefault("error", spinnakerHttpException.getReason());
        List<String> errors =
            (List<String>) body.getOrDefault("errors", body.getOrDefault("messages", List.of()));

        // SpinnakerHttpException includes the message property from the response
        // body along with other useful info into its "regular" exception message.
        // So, use that if there's no errors nor messages property.  Note that
        // this is what appears in the spinnaker UI.
        if (errors.isEmpty()) {
          errors = List.of(spinnakerHttpException.getMessage());
        }

        responseDetails = ExceptionHandler.responseDetails(error, errors);

        Object exceptionProperty = body.get("exception");
        if (exceptionProperty != null) {
          // Mirror what RetrofitExceptionHandler does.
          responseDetails.put("rootException", exceptionProperty);
        } else {
          // If there's no explicit exception property, include the stack trace in
          // the same way DefaultExceptionHandler does to get a stack trace in the
          // execution context.  It is tempting to leave this out to reduce the
          // size of the execution context, and assume that the info is available
          // in the log.
          responseDetails.put(
              "stackTrace", Throwables.getStackTraceAsString(spinnakerHttpException));
        }

        // RetrofitExceptionHandler includes the entire response body as a string.
        // This seems a bit much, as, in theory, SpinnakerHttpException extracts
        // all the relevant information already.  As well, the response could be
        // huge so it's likely not a good idea to try to include all of it.  As
        // well, by the time we get here, we likely no longer have access to the
        // response body.
      }

      responseDetails.put("status", spinnakerHttpException.getResponseCode());
      responseDetails.put("url", spinnakerHttpException.getUrl());
    } else {
      kind = "UNEXPECTED";
      responseDetails = ExceptionHandler.responseDetails(ex.getMessage());
      responseDetails.put("stackTrace", Throwables.getStackTraceAsString(ex));
    }

    responseDetails.put("kind", kind);

    // Although Spinnaker*Exception has a retryable property that other parts of
    // spinnaker use, ignore it here for compatibility with
    // RetrofitExceptionHandler, specifically because that doesn't retry (most)
    // POST requests which could be dangerous.
    return new ExceptionHandler.Response(
        ex.getClass().getSimpleName(),
        taskName,
        responseDetails,
        shouldRetry(ex, kind, responseCode));
  }
}
