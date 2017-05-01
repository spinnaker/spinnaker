/*
 * Copyright 2017 Microsoft, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Arrays;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class PersistentStore extends Node {
  @Override
  public String getNodeName() {
    return persistentStoreType().getId();
  }

  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeEmptyIterator();
  }

  abstract public PersistentStoreType persistentStoreType();

  public enum PersistentStoreType {
    S3("s3"),
    AZS("azs"),
    GCS("gcs");

    String id;

    PersistentStoreType(String id) {
      this.id = id;
    }

    @JsonValue
    public String getId() {
      return id;
    }

    @JsonCreator
    public static PersistentStoreType fromString(String value) {
      return Arrays.stream(values())
          .filter(v -> v.getId().equalsIgnoreCase(value))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Type " + value + " is not a valid persistent storage option. Choose from " + Arrays.toString(values())));
    }
  }
}
