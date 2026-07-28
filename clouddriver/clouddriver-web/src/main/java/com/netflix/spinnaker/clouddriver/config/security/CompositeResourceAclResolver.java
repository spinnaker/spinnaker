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

package com.netflix.spinnaker.clouddriver.config.security;

import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.ResourceType;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Routes an ACL lookup to the first delegate that can resolve it. Each delegate is type-guarded (it
 * returns {@code null} for resource types it does not own), so the first non-{@code null} result is
 * the authoritative one; a {@code null} from every delegate means "no ACL resolvable here" and the
 * enclosing evaluator applies its unknown-resource fallback.
 *
 * <p>Clouddriver composes its in-process {@link ClouddriverResourceAclResolver} ({@code account})
 * with the Front50-backed {@link Front50ApplicationAclResolver} ({@code application}) so both the
 * account and application {@code hasPermission(...)} checks evaluate against real ACLs.
 */
public class CompositeResourceAclResolver implements ResourceAclResolver {

  private final List<ResourceAclResolver> delegates;

  public CompositeResourceAclResolver(List<ResourceAclResolver> delegates) {
    this.delegates = List.copyOf(delegates);
  }

  @Nullable
  @Override
  public Permissions resolve(ResourceType resourceType, String resourceName) {
    for (ResourceAclResolver delegate : delegates) {
      Permissions acl = delegate.resolve(resourceType, resourceName);
      if (acl != null) {
        return acl;
      }
    }
    return null;
  }
}
