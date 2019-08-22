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
import com.google.common.collect.ImmutableSet;
import java.io.IOException;

/**
 * A simple interface for executing multiple paged requests in batches.
 *
 * <p>Queued {@link PaginatedComputeRequest requests} will be sent off in a single batch. If the any
 * of the responses indicate there are more pages of results, further batches will be sent until all
 * pages have been queried.
 */
public interface BatchPaginatedComputeRequest<RequestT extends ComputeRequest<?>, ItemT> {

  void queue(PaginatedComputeRequest<RequestT, ItemT> request);

  ImmutableSet<ItemT> execute(String batchContext) throws IOException;
}
