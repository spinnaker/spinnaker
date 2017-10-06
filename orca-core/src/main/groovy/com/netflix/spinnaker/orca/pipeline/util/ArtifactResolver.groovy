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
 */

package com.netflix.spinnaker.orca.pipeline.util

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import groovy.util.logging.Slf4j

import java.lang.reflect.Field

@Slf4j
class ArtifactResolver {

  static void resolveArtifacts(Map pipeline) {
    List<Artifact> resolvedArtifacts = []
    if (pipeline.trigger.expectedArtifacts) {
      // Resolve artifacts based on expectedArtifacts.
      List<Artifact> receivedArtifacts = pipeline.receivedArtifacts ?: []

      // Split up expected artifacts on whether they're satisfied or not.
      def (hasMatch, needsLookup) = pipeline.trigger.expectedArtifacts.split { e ->
        receivedArtifacts.any { a -> expectedMatch((ExpectedArtifact) e, (Artifact) a) }
      }

      // If satisfied, just find the artifact that matched.
      hasMatch?.each { e -> resolvedArtifacts << (receivedArtifacts.find { a -> expectedMatch((ExpectedArtifact) e, (Artifact) a) }) }

      // Otherwise, resolve artifact based on the missing fieldType and missingPolicy.
      needsLookup?.each { e ->
        receivedArtifacts.each { a ->
          e.fields?.each { field ->
            if (!lookupField((ExpectedArtifact.ArtifactField) field, (Artifact) a)) {
              switch (field.getFieldType()) {
                case ExpectedArtifact.ArtifactField.FieldType.MUST_MATCH:
                  throw new ArtifactResolutionException(
                      "Received artifact value did not match expected artifact for fieldName: ${field.getFieldName()}")
                  break
                case ExpectedArtifact.ArtifactField.FieldType.FIND_IF_MISSING:
                  switch (field.getMissingPolicy()) {
                    case ExpectedArtifact.ArtifactField.MissingPolicy.FAIL_PIPELINE:
                      throw new ArtifactResolutionException(
                          "Received artifact value missing, failing pipeline")
                      break
                    case ExpectedArtifact.ArtifactField.MissingPolicy.EXPRESSION:
                      throw new ArtifactResolutionException("Expression execution is not implemented yet")
                      break
                    case ExpectedArtifact.ArtifactField.MissingPolicy.PRIOR_PIPELINE:
                      throw new ArtifactResolutionException("Prior pipeline lookup is not implemented yet")
                      break
                    default:
                      throw new ArtifactResolutionException("Unrecognized expected artifact MissingPolicy, failing")
                      break
                  }
                  break
                default:
                  throw new ArtifactResolutionException("Unrecognized expected artifact FieldType")
                  break
              }
            }
          }
        }
      }
    } else if (pipeline.receivedArtifacts) {
      // We received artifacts but didn't expect any in the trigger, just add them to the context.
      resolvedArtifacts.addAll(pipeline.receivedArtifacts)
    }
    pipeline.trigger.artifacts = resolvedArtifacts
  }

  static Boolean expectedMatch(ExpectedArtifact e, Artifact a) {
    return e.getFields().every { field -> lookupField((ExpectedArtifact.ArtifactField) field, a) }
  }

  static private Boolean lookupField(ExpectedArtifact.ArtifactField field, Artifact a) {
    // Look up the field in the actual artifact and check that the values match.
    try {
      Field declaredField = a.getClass().getDeclaredField(field.getFieldName())
      declaredField.setAccessible(true)
      String actualValue = (String) declaredField.get(a) // Note: all fields we can match on are Strings.
      declaredField.setAccessible(false)
      return actualValue && actualValue.equals(field.getValue())
    } catch (IllegalAccessException | NoSuchFieldException ex) {
      log.error(ex.getMessage())
      return false
    }
  }

  static class ArtifactResolutionException extends RuntimeException {
    ArtifactResolutionException(String message) {
      super(message)
    }

  }
}
