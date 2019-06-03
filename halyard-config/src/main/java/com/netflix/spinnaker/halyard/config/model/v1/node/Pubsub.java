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
 *
 */

package com.netflix.spinnaker.halyard.config.model.v1.node;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class Pubsub<S extends Subscription, P extends Publisher> extends Node
    implements Cloneable {

  boolean enabled = false;

  public abstract List<S> getSubscriptions();

  public abstract List<P> getPublishers();

  @Override
  public NodeIterator getChildren() {
    Stream<Node> all = Stream.concat(getSubscriptions().stream(), getPublishers().stream());
    return NodeIteratorFactory.makeListIterator(all.collect(Collectors.toList()));
  }

  @Override
  public String getNodeName() {
    return getPubsubType().getName();
  }

  public abstract PubsubType getPubsubType();

  public enum PubsubType {
    GOOGLE("google");

    @Getter final String name;

    PubsubType(String name) {
      this.name = name;
    }
  }
}
