/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor.gcb;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.cloudbuild.v1.CloudBuildRequest;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Executes requests to the Google Cloud Build API, catching appropriate error conditions and
 * translating them into appropriate exceptions.
 */
@Component
@ConditionalOnProperty("gcb.enabled")
@Slf4j
public class GoogleCloudBuildExecutor {
  // TODO(ezimanyi): Consider adding retry logic here
  public <T> T execute(RequestFactory<T> requestFactory) {
    try {
      CloudBuildRequest<T> request = requestFactory.get();
      return request.execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 400) {
        log.error(e.getMessage());
        throw new InvalidRequestException(e.getMessage());
      } else if (e.getStatusCode() == 404) {
        throw new NotFoundException(e.getMessage());
      }
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  interface RequestFactory<T> {
    CloudBuildRequest<T> get() throws IOException;
  }
}
