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
 */

package com.netflix.spinnaker.halyard.config.model.v1.security;

import com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.Secret;
import com.netflix.spinnaker.halyard.config.model.v1.node.SecretFile;
import java.net.URL;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Saml extends AuthnMethod {

  private final Method method = Method.SAML;
  private final String nodeName = "saml";

  @LocalFile @SecretFile private String metadataLocal;
  private String metadataRemote;
  private String issuerId;

  @LocalFile @SecretFile private String keyStore;
  @Secret private String keyStorePassword;
  private String keyStoreAliasName;

  private URL serviceAddress;

  private String signatureDigest;

  private UserAttributeMapping userAttributeMapping = new UserAttributeMapping();

  @Data
  public static class UserAttributeMapping {
    private String firstName;
    private String lastName;
    private String roles;
    private String rolesDelimiter;
    private String username;
    private String email;
  }
}
