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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.Secret;
import com.netflix.spinnaker.halyard.config.model.v1.node.SecretFile;
import com.netflix.spinnaker.halyard.config.model.v1.security.Saml;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import java.net.URL;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode(callSuper = false)
@Data
public class SamlConfig {

  boolean enabled;

  String issuerId;

  @SecretFile(prefix = "file:")
  String metadataUrl;

  @LocalFile
  @SecretFile(prefix = "file:")
  String keyStore;

  @Secret String keyStorePassword;
  String keyStoreAliasName;

  String redirectProtocol;
  String redirectHostname;
  String redirectBasePath;

  String signatureDigest;

  Saml.UserAttributeMapping userAttributeMapping;

  public SamlConfig(Security security) {
    if (!security.getAuthn().getSaml().isEnabled()) {
      return;
    }

    Saml saml = security.getAuthn().getSaml();

    this.enabled = saml.isEnabled();
    this.issuerId = saml.getIssuerId();
    this.metadataUrl = saml.getMetadataLocal();
    if (StringUtils.isNotEmpty(saml.getMetadataRemote())) {
      this.metadataUrl = saml.getMetadataRemote();
    }
    this.keyStore = saml.getKeyStore();
    this.keyStoreAliasName = saml.getKeyStoreAliasName();
    this.keyStorePassword = saml.getKeyStorePassword();

    URL u = saml.getServiceAddress();
    this.redirectProtocol = u.getProtocol();
    this.redirectHostname = u.getHost();
    if (u.getPort() != -1) {
      this.redirectHostname += ":" + u.getPort();
    }
    if (StringUtils.isNotEmpty(u.getPath())) {
      this.redirectBasePath = u.getPath();
    }

    this.signatureDigest = saml.getSignatureDigest();

    this.userAttributeMapping = saml.getUserAttributeMapping();
  }
}
