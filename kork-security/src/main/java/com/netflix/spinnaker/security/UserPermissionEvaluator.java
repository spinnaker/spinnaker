/*
 * Copyright 2024 Salesforce, Inc.
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

package com.netflix.spinnaker.security;

import java.io.Serializable;
import org.springframework.security.access.PermissionEvaluator;

/**
 * Make it possible to authorize by username in kork (e.g. in S3ArtifactStoreGetter), as
 * FiatPermissionEvaluator currently does.
 */
public interface UserPermissionEvaluator extends PermissionEvaluator {

  boolean hasPermission(
      String username, Serializable resourceName, String resourceType, Object authorization);
}
