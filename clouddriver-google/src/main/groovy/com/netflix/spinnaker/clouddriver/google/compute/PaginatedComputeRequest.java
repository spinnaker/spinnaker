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
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public interface PaginatedComputeRequest<RequestT extends ComputeRequest<?>, ItemT> {

  void execute(Consumer<List<ItemT>> pageConsumer) throws IOException;

  /**
   * Return a version of this object with an updated request modifier (overwriting the existing one
   * if previously set).
   *
   * <p>The request modifier allows you to do things like set a filter on the outgoing requests.
   */
  PaginatedComputeRequest<RequestT, ItemT> withRequestModifier(Consumer<RequestT> requestModifier);

  default ImmutableList<ItemT> execute() throws IOException {
    ImmutableList.Builder<ItemT> result = ImmutableList.builder();
    execute(result::addAll);
    return result.build();
  }
}
