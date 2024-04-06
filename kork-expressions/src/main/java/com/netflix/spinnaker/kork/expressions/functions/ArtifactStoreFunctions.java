/*
 * Copyright 2024 Apple Inc.
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
package com.netflix.spinnaker.kork.expressions.functions;

import com.netflix.spinnaker.kork.api.expressions.ExpressionFunctionProvider;
import org.jetbrains.annotations.Nullable;

/** Houses all SpEL related functions that deal specifically with the artifact store */
public class ArtifactStoreFunctions implements ExpressionFunctionProvider {
  @Nullable
  @Override
  public String getNamespace() {
    return null;
  }

  /**
   * Used to return the original based64 reference that was stored by the artifact store. It's
   * important to note that utilizing this SpEL function will balloon the context, e.g. increasing
   * the size of the execution context.
   *
   * <p>{@code ${ #fetchReference(#stage('Bake Manifest').context.artifacts[0].reference) }}
   */
  public static String fetchReference(String ref) {
    // We do not have to do anything here since the artifact URI converter will
    // convert any references to the original stored reference
    //
    // One caveat with this function is that if there is nothing to retrieve,
    // the original string will just be returned
    return ref;
  }

  @Override
  public Functions getFunctions() {
    return new Functions(
        new FunctionDefinition(
            "fetchReference",
            "Retrieve artifact reference",
            new FunctionParameter(
                String.class,
                "ref",
                "Will return the associated artifact's base64 reference back.")));
  }
}
