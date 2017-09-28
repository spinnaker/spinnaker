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

package com.netflix.spinnaker.kork.artifacts.model;

import lombok.Data;
import org.apache.commons.lang.StringUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class ExpectedArtifact {
  private List<ArtifactField> fields = new ArrayList<>();

  private static final Set<String> artifactFields = Arrays.stream(Artifact.class.getFields())
      .filter(f -> f.getType().equals(String.class))
      .map(Field::getName)
      .collect(Collectors.toSet());

  @Data
  public static class ArtifactField {
    String fieldName;
    FieldType fieldType;
    String value;
    MissingPolicy missingPolicy;
    String expression;

    public enum FieldType {
      MUST_MATCH,
      FIND_IF_MISSING,
    }

    public enum MissingPolicy {
      FAIL_PIPELINE,
      EXPRESSION,
      PRIOR_PIPELINE,
    }
  }

  public void validate() {
    boolean matchCondition = false;
    for (ArtifactField field : fields) {
      if (!artifactFields.contains(field.getFieldName())) {
        throw new IllegalStateException("Unknown field '" + field.getFieldName() + "' does exist in the artifact definition");
      }

      if (field.getFieldType() == null) {
        throw new IllegalStateException("fieldType must be set.");
      }

      switch (field.getFieldType()) {
        case MUST_MATCH:
          matchCondition = true;
          break;
        case FIND_IF_MISSING:
          if (field.getMissingPolicy() == null) {
            throw new IllegalStateException("When fieldType == FIND_IF_MISSING, a policy must be provided");
          }

          switch (field.getMissingPolicy()) {
            case EXPRESSION:
              if (StringUtils.isEmpty(field.getExpression())) {
                throw new IllegalStateException("When missingPolicy == EXPRESSION, a given expression must be provided");
              }
              break;
            case FAIL_PIPELINE:
              break;
            case PRIOR_PIPELINE:
              break;
            default:
              throw new IllegalStateException("Unknown missing policy: " + field.getMissingPolicy());
          }

          break;
        default:
          throw new IllegalStateException("Unknown field type: " + field.getFieldType());
      }
    }

    if (!matchCondition) {
      throw new IllegalStateException("At least one field must be required to match against incoming artifacts");
    }
  }
}
