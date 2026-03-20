/*
 * Copyright 2025 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore.entities;

import com.netflix.spinnaker.kork.artifacts.ArtifactTypeDecorator;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactReferenceURI;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.exceptions.ArtifactStoreInvalidStateException;
import com.netflix.spinnaker.kork.artifacts.artifactstore.exceptions.ArtifactStoreInvalidTypeException;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;

/**
 * Used to expand manifests that live in the context, but only when a property is accessed directly.
 *
 * <p>SpEL expressions that just inspect the manifest will not expand it, but any access to a
 * property within the manifest will expand the manifest and return the proper value.
 *
 * <p><code>
 *     // This would appropriately expand the manifest to retrieve the value
 *     ${ #stage('Deploy (Manifest)').context.manifests[0].metadata }
 *     // Returns:
 *     //  {
 *     //    "name": "nginx",
 *     //    "labels": {
 *     //       "helm.sh/chart": "nginx-1.0.0",
 *     //       "app.kubernetes.io/managed-by": "Helm",
 *     //       "app.kubernetes.io/name": "nginx",
 *     //       "app.kubernetes.io/instance": "nginx",
 *     //       "app.kubernetes.io/version": "1.0.0"
 *     //    }
 *     //  }
 *     // as opposed to the expression below:
 *     ${ #stage('Deploy (Manifest)').context.manifests[0] }
 *     // Returns:
 *     // {
 *     //   "customKind": false,
 *     //   "reference": "ref://application/hash",
 *     //   "metadata": {},
 *     //   "name": "stored-manifest",
 *     //   "type": "remote/manifest/base64"
 *     // }
 * </code>
 */
public class EntityPropertyAccessor implements PropertyAccessor {
  private static final String NO_TYPE = "no-type";
  private static final String ARTIFACT_TYPE_KEY = "type";
  private static final String ARTIFACT_REFERENCE_KEY = "reference";

  private final List<String> aggressiveExpansionKeys;
  private final List<String> supportedTypes;

  public EntityPropertyAccessor(List<String> supportedTypes, List<String> aggressiveExpansionKeys) {
    this.supportedTypes = supportedTypes;
    this.aggressiveExpansionKeys = aggressiveExpansionKeys;
  }

  @Override
  public Class<?>[] getSpecificTargetClasses() {
    return new Class[] {Map.class, Artifact.class};
  }

  @Override
  public boolean canRead(EvaluationContext context, Object target, String name) {
    if (ArtifactStore.getInstance() == null) {
      return false;
    }
    if (this.aggressiveExpansionKeys.stream().anyMatch(k -> k.equals(name))) {
      return true;
    }

    String type = extractType(target);
    return this.supportedTypes.stream().anyMatch(t -> t.equals(type));
  }

  @Override
  public TypedValue read(EvaluationContext context, Object target, String name)
      throws AccessException {
    if (this.aggressiveExpansionKeys.stream().anyMatch(k -> k.equals(name))) {
      return new TypedValue(handleExpansionKey((Map) target, name));
    }

    String type = extractType(target);
    if (this.supportedTypes.stream().noneMatch(t -> t.equals(type))) {
      throw new ArtifactStoreInvalidTypeException(type);
    }

    if (target instanceof Map) {
      return new TypedValue(handleMap((Map) target, name));
    }
    throw new ArtifactStoreInvalidStateException(
        String.format(
            "Expected a map or artifact but received type: %s", target.getClass().getName()));
  }

  private Object handleExpansionKey(Map target, String name) {
    Object v = target.get(name);
    if (v instanceof List) {
      List<Object> l = (List) v;
      List<Object> ret = new ArrayList<>();
      for (Object elem : l) {
        if (!(elem instanceof Map)) {
          ret.add(elem);
          continue;
        }

        ret.add(handleMap((Map) elem, null));
      }

      return ret;
    } else if (v instanceof Map) {
      return handleMap((Map) v, null);
    }

    return target;
  }

  private Object handleMap(Map target, String name) {
    ArtifactStore storage = ArtifactStore.getInstance();
    String ref = (String) target.get(ARTIFACT_REFERENCE_KEY);
    // The purpose of this is to allows users to use the #fetchReference SpEL function. Otherwise,
    // this would fail when calling artifactMap.get(name), since reference may not be a valid field
    // in
    // the manifest object.
    if (ARTIFACT_REFERENCE_KEY.equals(name) && ArtifactReferenceURI.is(ref)) {
      return ref;
    }
    Artifact artifact =
        storage.get(
            ArtifactReferenceURI.parse(ref),
            new ArtifactTypeDecorator(ArtifactTypes.EMBEDDED_BASE64));
    Map artifactMap = EntityHelper.toMap(artifact);
    if (name == null) {
      return artifactMap;
    }

    return artifactMap.get(name);
  }

  @Override
  public boolean canWrite(EvaluationContext context, Object target, String name)
      throws AccessException {
    return false;
  }

  @Override
  public void write(EvaluationContext context, Object target, String name, Object newValue)
      throws AccessException {}

  /**
   * Used to extract the artifact type from the target
   *
   * <p>Artifacts have a known structure, and due to the mapping to Maps instead of the Artifact
   * class, we have to "guess" whether we are looking at an artifact or not. We do this by looking
   * at two required fields in an artifact, 'type' and 'reference'. If the type field matches an
   * artifact type, and we have a reference key of a string type.
   */
  private static String extractType(Object target) {
    if (target instanceof Artifact) {
      return ((Artifact) target).getType();
    }
    if (!(target instanceof Map)) {
      return NO_TYPE;
    }

    Map m = (Map) target;
    if (!(m.containsKey(ARTIFACT_TYPE_KEY) && m.containsKey(ARTIFACT_REFERENCE_KEY))) {
      return NO_TYPE;
    }

    Object typeObj = m.get(ARTIFACT_TYPE_KEY);
    if (typeObj instanceof String) {
      return (String) m.get(ARTIFACT_TYPE_KEY);
    }
    return NO_TYPE;
  }
}
