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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.saml;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.AbstractEditAuthnMethodCommand;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.security.AuthnMethod;
import com.netflix.spinnaker.halyard.config.model.v1.security.Saml;
import lombok.Getter;

import java.net.MalformedURLException;
import java.net.URL;

@Parameters(separators = "=")
public class EditSamlCommand extends AbstractEditAuthnMethodCommand<Saml> {

  @Getter
  private String shortDescription = "Configure authentication using a SAML identity provider.";

  @Getter
  private String longDescription = String.join(" ",
    "SAML authenticates users by passing cryptographically signed XML documents between the Gate",
    "server and an identity provider. Gate's key is stored and accessed via the --keystore ",
    "parameters, while the identity provider's keys are included in the metadata.xml. Finally,",
    "the identity provider must redirect the control flow (through the user's browser) back to",
    "Gate by way of the --serviceAddressUrl. This is likely the address of Gate's load balancer."
  );

  @Getter
  private AuthnMethod.Method method = AuthnMethod.Method.SAML;

  @Parameter(
      names = "--metadata",
      description = "The address to your identity provider's metadata XML file. This can be a URL" +
          " or the path of a local file."
  )
  private String metadata;

  @Parameter(
      names = "--issuer-id",
      description = "The identity of the Spinnaker application registered with the SAML provider."
  )
  private String issuerId;

  @Parameter(
      names = "--keystore",
      description = "Path to the keystore that contains this server's private key. This key is " +
          "used to cryptographically sign SAML AuthNRequest objects."
  )
  private String keystore;

  @Parameter(
      names = "--keystore-password",
      description = "The password used to access the file specified in --keystore"
  )
  private String keystorePassword;

  @Parameter(
      names = "--keystore-alias",
      description = "The name of the alias under which this server's private key is stored in the" +
          " --keystore file."
  )
  private String keystoreAliasName;

  @Parameter(
      names = "--service-address-url",
      description = "The address of the Gate server that will be accesible by the SAML identity " +
          "provider. This should be the full URL, including port, e.g. https://gate.org.com:8084/" +
          ". If deployed behind a load balancer, this would be the laod balancer's address."
  )
  private URL serviceAddress;

  @Override
  protected AuthnMethod editAuthnMethod(Saml s) {
    s.setIssuerId(isSet(issuerId) ? issuerId : s.getIssuerId());
    s.setKeyStore(isSet(keystore) ? keystore : s.getKeyStore());
    s.setKeyStorePassword(isSet(keystorePassword) ? keystorePassword : s.getKeyStorePassword());
    s.setKeyStoreAliasName(isSet(keystoreAliasName) ? keystoreAliasName : s.getKeyStoreAliasName());
    s.setServiceAddress(isSet(serviceAddress) ? serviceAddress : s.getServiceAddress());

    if (isSet(metadata)) {
      if (metadata.startsWith("http")) {
        s.setMetadataRemote(metadata);
        s.setMetadataLocal(null);
      } else {
        s.setMetadataLocal(metadata);
        s.setMetadataRemote(null);
      }
    }

    return s;
  }
}
