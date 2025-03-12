/*
 * Copyright 2023 Apple Inc.
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
package com.netflix.spinnaker.kork.expressions;

import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactReferenceURI;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.spel.support.StandardTypeConverter;

/**
 * This converter is used to check if a String is a Artifact reference URI. If it is, this will then
 * pull the reference from the artifact store and return the reference back base64 encoded.
 */
public class ArtifactUriToReferenceConverter implements TypeConverter {

  private final ArtifactStore artifactStore;

  public ArtifactUriToReferenceConverter(ArtifactStore artifactStore) {
    this.artifactStore = artifactStore;
  }

  private final StandardTypeConverter defaultTypeConverter = new StandardTypeConverter();

  @Override
  public boolean canConvert(TypeDescriptor sourceType, @NotNull TypeDescriptor targetType) {
    return isArtifactUriType(sourceType, targetType)
        || defaultTypeConverter.canConvert(sourceType, targetType);
  }

  private boolean isArtifactUriType(TypeDescriptor sourceType, @NotNull TypeDescriptor targetType) {
    return sourceType != null
        && sourceType.getObjectType() == String.class
        && targetType.getObjectType() == String.class;
  }

  @Override
  public Object convertValue(
      Object value, TypeDescriptor sourceType, @NotNull TypeDescriptor targetType) {
    if (!isArtifactUriType(sourceType, targetType)) {
      return defaultTypeConverter.convertValue(value, sourceType, targetType);
    }

    if (artifactStore == null || !ArtifactReferenceURI.is((String) value)) {
      return defaultTypeConverter.convertValue(value, sourceType, targetType);
    }

    return artifactStore.get(ArtifactReferenceURI.parse((String) value)).getReference();
  }
}
