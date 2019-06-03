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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.api;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.security.SpringSsl;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.web.server.Ssl;

@EqualsAndHashCode(callSuper = true)
@Data
@Parameters(separators = "=")
public class SpringSslEditCommand extends AbstractConfigCommand {
  private String commandName = "edit";

  private String shortDescription = "Edit SSL settings for your API server.";

  private String longDescription =
      "Configure SSL termination to handled by the API server's Tomcat server.";

  @Parameter(
      names = "--key-alias",
      description = "Name of your keystore entry as generated with your keytool.")
  String keyAlias;

  @Parameter(
      names = "--keystore",
      converter = LocalFileConverter.class,
      description = "Path to the keystore holding your security certificates.")
  String keyStore;

  @Parameter(
      names = "--keystore-type",
      description = "The type of your keystore. Examples include JKS, and PKCS12.")
  String keyStoreType;

  @Parameter(
      names = "--keystore-password",
      password = true,
      description =
          "The password to unlock your keystore. Due to a limitation in Tomcat, this must match "
              + "your key's password in the keystore.")
  String keyStorePassword;

  @Parameter(
      names = "--truststore",
      converter = LocalFileConverter.class,
      description = "Path to the truststore holding your trusted certificates.")
  String trustStore;

  @Parameter(
      names = "--truststore-type",
      description = "The type of your truststore. Examples include JKS, and PKCS12.")
  String trustStoreType;

  @Parameter(
      names = "--truststore-password",
      password = true,
      description = "The password to unlock your truststore.")
  String trustStorePassword;

  @Parameter(
      names = "--client-auth",
      description =
          "Declare 'WANT' when client auth is wanted but not mandatory, "
              + "or 'NEED', when client auth is mandatory.")
  Ssl.ClientAuth clientAuth;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    SpringSsl springSsl =
        new OperationHandler<SpringSsl>()
            .setOperation(Daemon.getSpringSsl(currentDeployment, false))
            .setFailureMesssage("Failed to load SSL settings.")
            .get();

    int originalHash = springSsl.hashCode();

    springSsl.setKeyAlias(isSet(keyAlias) ? keyAlias : springSsl.getKeyAlias());
    springSsl.setKeyStore(isSet(keyStore) ? keyStore : springSsl.getKeyStore());
    springSsl.setKeyStoreType(isSet(keyStoreType) ? keyStoreType : springSsl.getKeyStoreType());
    springSsl.setKeyStorePassword(
        isSet(keyStorePassword) ? keyStorePassword : springSsl.getKeyStorePassword());
    springSsl.setTrustStore(isSet(trustStore) ? trustStore : springSsl.getTrustStore());
    springSsl.setTrustStoreType(
        isSet(trustStoreType) ? trustStoreType : springSsl.getTrustStoreType());
    springSsl.setTrustStorePassword(
        isSet(trustStorePassword) ? trustStorePassword : springSsl.getTrustStorePassword());
    springSsl.setClientAuth(isSet(clientAuth) ? clientAuth : springSsl.getClientAuth());

    if (originalHash == springSsl.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setSpringSsl(currentDeployment, !noValidate, springSsl))
        .setFailureMesssage("Failed to edit SSL settings.")
        .setFailureMesssage("Successfully updated SSL settings.")
        .get();
  }
}
