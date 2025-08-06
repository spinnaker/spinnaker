/*
 * Copyright 2022 Armory, Inc.
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

package com.netflix.spinnaker.kork.expressions.config;

import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Spring Expression Language (SpEL) evaluation related features. */
@Data
@ConfigurationProperties(prefix = "expression")
public class ExpressionProperties {

  /** Flag to determine if SpEL evaluation to be skipped. */
  private final FeatureFlag doNotEvalSpel = new FeatureFlag().setEnabled(true);

  /**
   * To set the maximum limit of characters in expression for SpEL evaluation. Default value -1
   * signifies to use default maximum limit of 10,000 characters provided by springframework.
   */
  private int maxExpressionLength = -1;

  /**
   * Used within the {@link
   * com.netflix.spinnaker.kork.artifacts.artifactstore.entities.EntityStorePropertyAccessor}, and
   * if the type matches the SpEL that is being evaluated in the property accessor, we will expand
   * that artifact.
   *
   * <p>As an example assume we have the SpEL expression
   *
   * <pre>
   *   // assume the stage execution JSON looks something like
   *   {
   *     "context": {
   *       "manifests": [
   *          {
   *            "type": "remote/map/base64",
   *            "reference": "ref://myapplication/hash"
   *          },
   *          {
   *            "type": "remote/customtype/base64",
   *            "reference": "ref://myapplication/anotherhash"
   *          }
   *       ]
   *     }
   *   }
   *
   *   // The SpEL expression would expand the first element in manifests
   *   ${ #stage('Deployment').context.manifests[0].metadata }
   *
   *   // However, this would remain as an artifact due to not being in the expansion SpEL list.
   *   ${ #stage('Deployment').context.manifests[1].metadata }
   * </pre>
   */
  private List<String> propertyExpansionTypes =
      List.of(ArtifactTypes.REMOTE_MAP_BASE64.getMimeType());

  /**
   * When using aggressiveExpansionKeys it will expand ALL elements when a SpEL expression matches
   * against a key in the aggressive list, regardless of the artifact types.
   *
   * <p>For example, if we included the key, "manifests", then when a user uses the SpEL expression
   *
   * <pre>
   *   ${ #stage('Deployment').context.manifests }
   * </pre>
   *
   * This would print the full manifests list as opposed to printing the list of artifacts. The
   * primary reason why a key may exist in here is if someone is using Spinnaker to observe ALL
   * manifest outputs. It's also important to note that this expression also returns an artifact and
   * NOT the manifest:
   *
   * <pre>
   *   ${ #stage('Deployment').context.manifests[0] }
   * </pre>
   *
   * This is mainly wanting to keep the size of expansion as limited as possible. A lot of the times
   * Spinnaker users care about a particular value in a manifest, and not the full manifest
   * themselves. However, if you have use cases where your users need the full manifest or any other
   * key that due to a custom type and handler.
   */
  private List<String> aggressiveExpansionKeys = List.of();

  @Data
  @Accessors(chain = true)
  public static class FeatureFlag {
    private boolean enabled;
  }
}
