/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.Compute.InstanceGroupManagers;
import com.google.api.services.compute.Compute.RegionInstanceGroupManagers;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.Operation;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import java.io.IOException;
import java.util.List;

/**
 * A wrapper around {@link InstanceGroupManagers} and {@link RegionInstanceGroupManagers} that
 * performs operations on a specific {@link GoogleServerGroup}.
 *
 * <p><b>Note on {@code patch} vs {@code update}:</b> In the stable Compute v1 API, both {@code
 * instanceGroupManagers.patch} and {@code instanceGroupManagers.update} use HTTP PATCH with <a
 * href="https://tools.ietf.org/html/rfc7386">JSON merge patch</a> semantics. There is no PUT-based
 * replacement endpoint. Both methods in this interface therefore delegate to the same underlying
 * {@code managers.patch()} call. {@code update} is retained for source compatibility with existing
 * callers.
 *
 * @see <a
 *     href="https://cloud.google.com/compute/docs/reference/rest/v1/instanceGroupManagers/patch">
 *     instanceGroupManagers.patch (v1)</a>
 * @see <a
 *     href="https://cloud.google.com/compute/docs/reference/rest/v1/instanceGroupManagers/update">
 *     instanceGroupManagers.update (v1) — identical PATCH semantics</a>
 */
public interface GoogleServerGroupManagers {

  GoogleComputeOperationRequest<ComputeRequest<Operation>> abandonInstances(List<String> instances)
      throws IOException;

  GoogleComputeOperationRequest<ComputeRequest<Operation>> delete() throws IOException;

  GoogleComputeGetRequest<ComputeRequest<InstanceGroupManager>, InstanceGroupManager> get()
      throws IOException;

  /** Partial update using JSON merge patch (RFC 7386). Preferred for targeted field changes. */
  GoogleComputeOperationRequest patch(InstanceGroupManager content) throws IOException;

  /**
   * Retained for source compatibility. In Compute v1 this is identical to {@link #patch} — both use
   * HTTP PATCH with JSON merge patch semantics. There is no PUT-based full-replacement endpoint for
   * instance group managers in the stable API.
   *
   * @see <a
   *     href="https://cloud.google.com/compute/docs/reference/rest/v1/instanceGroupManagers/update">
   *     v1 update docs (redirects to patch)</a>
   */
  GoogleComputeOperationRequest<ComputeRequest<Operation>> update(InstanceGroupManager content)
      throws IOException;
}
