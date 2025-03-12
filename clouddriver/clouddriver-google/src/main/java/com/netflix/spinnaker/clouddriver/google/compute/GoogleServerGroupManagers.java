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
 */
public interface GoogleServerGroupManagers {

  GoogleComputeOperationRequest<ComputeRequest<Operation>> abandonInstances(List<String> instances)
      throws IOException;

  GoogleComputeOperationRequest<ComputeRequest<Operation>> delete() throws IOException;

  GoogleComputeGetRequest<ComputeRequest<InstanceGroupManager>, InstanceGroupManager> get()
      throws IOException;

  GoogleComputeOperationRequest patch(InstanceGroupManager content) throws IOException;

  GoogleComputeOperationRequest<ComputeRequest<Operation>> update(InstanceGroupManager content)
      throws IOException;
}
