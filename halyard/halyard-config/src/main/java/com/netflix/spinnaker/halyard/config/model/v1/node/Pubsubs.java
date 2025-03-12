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

import com.netflix.spinnaker.halyard.config.model.v1.pubsub.google.GooglePubsub;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class Pubsubs extends Node implements Cloneable {
  private Boolean enabled;
  private GooglePubsub google = new GooglePubsub();

  @Override
  public String getNodeName() {
    return "pubsub";
  }

  public Boolean getEnabled() {
    NodeIterator pubsubNodes = getChildren();
    Pubsub pubsub;
    Boolean allEnabled = true;
    while ((pubsub = (Pubsub) pubsubNodes.getNext()) != null) {
      allEnabled &= pubsub.isEnabled();
    }
    return allEnabled;
  }

  public void setEnabled() {
    enabled = getEnabled();
  }

  public static Class<? extends Pubsub> translatePubsubType(String pubsubName) {
    Optional<? extends Class<?>> res =
        Arrays.stream(Pubsubs.class.getDeclaredFields())
            .filter(f -> f.getName().equals(pubsubName))
            .map(Field::getType)
            .findFirst();

    if (res.isPresent()) {
      return (Class<? extends Pubsub>) res.get();
    } else {
      throw new IllegalArgumentException(
          "No pubsub with name \"" + pubsubName + "\" handled by halyard");
    }
  }

  public static Class<? extends Subscription> translateSubscriptionType(String pubsubName) {
    Class<? extends Pubsub> pubsubClass = translatePubsubType(pubsubName);

    String subscriptionClassName = pubsubClass.getName().replaceAll("Pubsub", "Subscription");
    try {
      return (Class<? extends Subscription>) Class.forName(subscriptionClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "No subscription for class \"" + subscriptionClassName + "\" found", e);
    }
  }

  public static Class<? extends Publisher> translatePublisherType(String pubsubName) {
    Class<? extends Pubsub> pubsubClass = translatePubsubType(pubsubName);

    String publisherClassName = pubsubClass.getName().replaceAll("Pubsub", "Publisher");
    try {
      return (Class<? extends Publisher>) Class.forName(publisherClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "No publisher for class \"" + publisherClassName + "\" found", e);
    }
  }
}
