/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.config.model.v1.node;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class HasImageProvider<A extends Account, B extends BakeryDefaults>
    extends Provider<A> implements Cloneable {
  private B bakeryDefaults = emptyBakeryDefaults();

  // When a user config deletes the bakery defaults, we want to be able to repopulate this. Due to
  // type erasure
  // the call to B's constructor must be done in the subclass of HasImageProvider.
  public abstract B emptyBakeryDefaults();

  @Override
  public NodeIterator getChildren() {
    NodeIterator parent = super.getChildren();
    NodeIterator thisIterator = NodeIteratorFactory.makeSingletonIterator(bakeryDefaults);
    return NodeIteratorFactory.makeAppendNodeIterator(parent, thisIterator);
  }
}
