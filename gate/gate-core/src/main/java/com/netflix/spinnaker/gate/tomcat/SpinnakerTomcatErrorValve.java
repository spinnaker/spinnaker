/*
 * Copyright 2025 Salesforce, Inc.
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
 */

package com.netflix.spinnaker.gate.tomcat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.coyote.ActionCode;
import org.springframework.http.MediaType;

/**
 * Inspired by https://github.com/spring-projects/spring-boot/issues/21257#issuecomment-745565376
 * and
 * https://stackoverflow.com/questions/63034757/force-tomcat-a-json-response-of-badrequest-error-invalid-character-found-in-the.
 * If this works well, it likely belongs in kork.
 */
@Slf4j
public class SpinnakerTomcatErrorValve extends ErrorReportValve {

  private final ObjectMapper objectMapper;

  // Tomcat needs a zero argument constructor.  It doesn't know how to use something like
  //
  // public SpinnakerTomcatErrorValve(ObjectMapper objectMapper) {
  //
  // so, construct our own ObjectMapper.
  public SpinnakerTomcatErrorValve() {
    super();
    this.objectMapper = new ObjectMapper();
  }

  @Override
  protected void report(Request request, Response response, Throwable throwable) {
    int statusCode = response.getStatus();
    log.info("SpinnakerTomcatErrorValve.report entry point: status code: {}", statusCode);
    if (statusCode < 400) {
      // Let the base class handle everything that's not an error
      super.report(request, response, throwable);
      return;
    }

    // Copied from ErrorReportValve.report
    //
    // Do nothing if anything has been written already
    // Do nothing if the response hasn't been explicitly marked as in error
    // and that error has not been reported.
    if (response.getContentWritten() > 0 || !response.setErrorReported()) {
      return;
    }

    // If an error has occurred that prevents further I/O, don't waste time
    // producing an error report that will never be read
    AtomicBoolean result = new AtomicBoolean(false);
    response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, result);
    if (!result.get()) {
      return;
    }

    // Build attributes similar to spring boot's
    // DefaultErrorAttributes.getErrorAttributes: timestamp, status, error (http
    // reason), exception (class name), message (exception message).
    Map<String, Object> errorAttributes = new HashMap<>();
    errorAttributes.put("timestamp", new Date());
    errorAttributes.put("status", response.getStatus());
    errorAttributes.put("error", response.getMessage());
    if (throwable != null) {
      errorAttributes.put("exception", throwable.getClass().getName());
      errorAttributes.put("message", throwable.getMessage());
    }

    String responseBody;
    try {
      responseBody = objectMapper.writeValueAsString(errorAttributes);
    } catch (JsonProcessingException e) {
      log.error("error building response body for {}", errorAttributes, e);
      return;
    }

    if (responseBody == null) {
      return;
    }

    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.toString());

    try {
      Writer writer = response.getReporter();
      if (writer != null) {
        // If writer is null, it's an indication that the response has
        // been hard committed already, which should never happen.
        writer.write(responseBody);
        response.finishResponse();
      }
    } catch (IOException e) {
      log.error("error writing response body for {}", errorAttributes, e);
    }
  }
}
