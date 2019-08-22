/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.ComputeRequest;
import com.netflix.spectator.api.Registry;
import java.util.Map;

final class GoogleComputeGetRequestImpl<RequestT extends ComputeRequest<ResponseT>, ResponseT>
    extends GoogleComputeRequestImpl<RequestT, ResponseT>
    implements GoogleComputeGetRequest<RequestT, ResponseT> {

  GoogleComputeGetRequestImpl(
      RequestT request, Registry registry, String metricName, Map<String, String> tags) {
    super(request, registry, metricName, tags);
  }
}
