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

package com.netflix.spinnaker.security.authz.pdp.acl;

import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.security.acls.domain.AclAuthorizationStrategy;
import org.springframework.security.acls.domain.AclImpl;
import org.springframework.security.acls.domain.AuditLogger;
import org.springframework.security.acls.domain.ConsoleAuditLogger;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.model.Acl;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Permission;

/**
 * Synthesizes a Spring Security ACL {@link Acl} on the fly from a resource's embedded {@link
 * Permissions}. No ACL data is persisted/migrated: roles -&gt; {@link GrantedAuthoritySid}; {@link
 * Authorization} -&gt; {@link Permission} bits via {@link SpinnakerPermissionFactory}; resource
 * -&gt; {@link ObjectIdentity}.
 *
 * <p>This is the "option B-adapter" referenced by the RBAC plan; the later JDBC-table migration
 * (option A) is a storage swap behind the same {@code AclService} that consumes these Acls.
 */
public final class AclSynthesizer {

  private static final AclAuthorizationStrategy NO_OP_AUTHORIZATION_STRATEGY =
      (acl, changeType) -> {
        // Synthesized Acls are read-only and never persisted, so administrative security checks on
        // mutation are intentionally skipped.
      };

  private static final AuditLogger AUDIT_LOGGER = new ConsoleAuditLogger();

  private AclSynthesizer() {}

  /** Builds a read-only {@link Acl} for {@code objectIdentity} from the supplied {@code acl}. */
  public static Acl synthesize(ObjectIdentity objectIdentity, Permissions acl) {
    AclImpl synthesized =
        new AclImpl(
            objectIdentity,
            objectIdentity.getIdentifier(),
            NO_OP_AUTHORIZATION_STRATEGY,
            AUDIT_LOGGER);

    int index = 0;
    for (Map.Entry<Authorization, Set<String>> entry : acl.unpack().entrySet()) {
      Permission permission = SpinnakerPermissionFactory.toPermission(entry.getKey());
      for (String group : entry.getValue()) {
        ((MutableAcl) synthesized)
            .insertAce(index++, permission, sidForRole(group), /* granting= */ true);
      }
    }
    return synthesized;
  }

  /**
   * Spring ACL sid for a Spinnaker role name (normalized + prefixed to match Spring authorities).
   */
  public static GrantedAuthoritySid sidForRole(String role) {
    return new GrantedAuthoritySid("ROLE_" + role.toLowerCase(Locale.ROOT));
  }
}
