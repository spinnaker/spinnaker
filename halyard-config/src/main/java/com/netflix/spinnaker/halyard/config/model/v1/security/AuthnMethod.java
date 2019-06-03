/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 */

package com.netflix.spinnaker.halyard.config.model.v1.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIteratorFactory;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public abstract class AuthnMethod extends Node {
  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeEmptyIterator();
  }

  private boolean enabled;

  @JsonIgnore
  public abstract Method getMethod();

  public static Class<? extends AuthnMethod> translateAuthnMethodName(String authnMethodName) {
    Optional<? extends Class<?>> res =
        Arrays.stream(Authn.class.getDeclaredFields())
            .filter(f -> f.getName().equals(authnMethodName))
            .map(Field::getType)
            .findFirst();

    if (res.isPresent()) {
      return (Class<? extends AuthnMethod>) res.get();
    } else {
      throw new IllegalArgumentException(
          "No authn method with name \"" + authnMethodName + "\" handled by halyard");
    }
  }

  public enum Method {
    OAuth2("oauth2"),
    SAML("saml"),
    LDAP("ldap"),
    X509("x509"),
    IAP("iap");

    public final String id;

    Method(String id) {
      this.id = id;
    }
  }
}
