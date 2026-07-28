/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.security.authz.application;

import java.util.Map;
import javax.annotation.Nullable;

/**
 * Reads a single application's permission record from its authoritative owner (Front50's {@code GET
 * /permissions/applications/{app}}).
 *
 * <p>This exists so {@link RemoteApplicationAclResolver} can live in kork and be shared by every
 * consuming service: each service supplies its own implementation bound to its own Front50 client,
 * keeping the service-specific Retrofit interfaces out of kork.
 *
 * <p>Implementations should read exactly one application. Deliberately no bulk variant: Front50
 * serves {@code findById} as a single-row read, while its "all permissions" endpoint calls {@code
 * StorageServiceSupport.all()}, which forces a full refresh of both the permission and application
 * collections — far more expensive than a handful of single-record reads.
 */
@FunctionalInterface
public interface ApplicationPermissionSource {

  /**
   * @param applicationName the application whose permission record to read
   * @return the raw permission record (a {@code name} + {@code permissions} map), or {@code null}
   *     when the application has no permission record or the record could not be read
   */
  @Nullable
  Map<?, ?> fetch(String applicationName);
}
