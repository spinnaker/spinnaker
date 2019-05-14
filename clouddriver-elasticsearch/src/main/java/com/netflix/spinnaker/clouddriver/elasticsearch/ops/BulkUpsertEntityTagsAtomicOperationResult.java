/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch.ops;

import com.netflix.spinnaker.clouddriver.model.EntityTags;
import java.util.ArrayList;
import java.util.Collection;

public class BulkUpsertEntityTagsAtomicOperationResult {

  public Collection<EntityTags> upserted = new ArrayList<>();
  public Collection<UpsertFailureResult> failures = new ArrayList<>();

  public static class UpsertFailureResult {
    EntityTags tag;
    Exception e;

    UpsertFailureResult(EntityTags tag, Exception e) {
      this.tag = tag;
      this.e = e;
    }
  }
}
