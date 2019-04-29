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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.client.json.GenericJson;
import com.google.api.services.cloudbuild.v1.CloudBuildRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GoogleCloudBuildExecutorTest {
  private GoogleCloudBuildExecutor executor = new GoogleCloudBuildExecutor();

  @SuppressWarnings("unchecked")
  private CloudBuildRequest<GenericJson> request =
      (CloudBuildRequest<GenericJson>) mock(CloudBuildRequest.class);

  @Test
  public void executesRequest() throws Exception {
    GenericJson mockResult = mock(GenericJson.class);
    when(request.execute()).thenReturn(mockResult);

    GenericJson result = executor.execute(() -> request);
    assertSame(mockResult, result);
  }
}
