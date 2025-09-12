/*
 * Copyright 2022 Apple Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.secrets.user;

import com.netflix.spinnaker.kork.ClassScanner;
import java.util.stream.Stream;
import org.springframework.core.io.ResourceLoader;

/**
 * Provides subtypes of {@link UserSecretData} for registration as user secret types. All beans of
 * this type contribute zero or more user secret classes. Classes must be annotated with {@link
 * UserSecretType} to indicate their secret type as said type is externalized into metadata from a
 * secret engine.
 */
public interface UserSecretTypeProvider {
  Stream<? extends Class<? extends UserSecretData>> getUserSecretTypes();

  static UserSecretTypeProvider fromPackage(String basePackage, ResourceLoader loader) {
    ClassScanner<UserSecretData> userSecretDataClassScanner =
        ClassScanner.forBaseType(UserSecretData.class).addLoadablePackage(loader, basePackage);
    return () -> userSecretDataClassScanner.scan().stream();
  }
}
