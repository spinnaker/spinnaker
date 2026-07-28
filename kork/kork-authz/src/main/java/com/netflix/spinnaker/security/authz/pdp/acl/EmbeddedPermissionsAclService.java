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

import com.netflix.spinnaker.security.authz.Permissions;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.acls.model.Acl;
import org.springframework.security.acls.model.AclService;
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Sid;

/**
 * A thin {@link AclService} that synthesizes {@link Acl}s on the fly from a {@link
 * PermissionsSource} rather than reading from the Spring ACL JDBC schema. This is the adapter that
 * lets {@code AclPermissionEvaluator} run against a resource's already-embedded {@link Permissions}
 * with no ACL data migration.
 */
public class EmbeddedPermissionsAclService implements AclService {

  private final PermissionsSource permissionsSource;

  public EmbeddedPermissionsAclService(PermissionsSource permissionsSource) {
    this.permissionsSource = permissionsSource;
  }

  @Override
  public List<ObjectIdentity> findChildren(ObjectIdentity parentIdentity) {
    // Synthesized Acls have no hierarchy.
    return Collections.emptyList();
  }

  @Override
  public Acl readAclById(ObjectIdentity object) throws NotFoundException {
    return readAclById(object, null);
  }

  @Override
  public Acl readAclById(ObjectIdentity object, List<Sid> sids) throws NotFoundException {
    Permissions permissions = permissionsSource.getPermissions(object);
    if (permissions == null) {
      throw new NotFoundException("No ACL found for " + object);
    }
    return AclSynthesizer.synthesize(object, permissions);
  }

  @Override
  public Map<ObjectIdentity, Acl> readAclsById(List<ObjectIdentity> objects)
      throws NotFoundException {
    return readAclsById(objects, null);
  }

  @Override
  public Map<ObjectIdentity, Acl> readAclsById(List<ObjectIdentity> objects, List<Sid> sids)
      throws NotFoundException {
    Map<ObjectIdentity, Acl> result = new LinkedHashMap<>();
    for (ObjectIdentity object : objects) {
      result.put(object, readAclById(object, sids));
    }
    return result;
  }
}
