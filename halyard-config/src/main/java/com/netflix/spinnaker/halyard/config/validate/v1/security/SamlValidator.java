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

package com.netflix.spinnaker.halyard.config.validate.v1.security;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.security.Saml;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.stereotype.Component;

@Component
public class SamlValidator extends Validator<Saml> {

  // Known algorithms supported by Gate (SamlSsoConfig.SignatureAlgorithms).
  // This is a copy of the known names so far, so we don't create a hard binary dependency between
  // Halyard and Gate.
  public static Collection<String> KNOWN_DIGEST_ALGORITHMS =
      Arrays.asList("SHA1", "SHA256", "SHA384", "SHA512", "RIPEMD160", "MD5");

  @Override
  public void validate(ConfigProblemSetBuilder p, Saml saml) {
    if (!saml.isEnabled()) {
      return;
    }

    if (StringUtils.isEmpty(saml.getMetadataLocal())
        && StringUtils.isEmpty(saml.getMetadataRemote())) {
      p.addProblem(Problem.Severity.ERROR, "No metadata file specified.");
    }

    if (StringUtils.isNotEmpty(saml.getMetadataLocal())) {
      try {
        new File(new URI("file:" + validatingFileDecryptPath(saml.getMetadataLocal())));
      } catch (Exception f) {
        p.addProblem(Problem.Severity.ERROR, f.getMessage());
      }
    }

    if (StringUtils.isNotEmpty(saml.getMetadataRemote())) {
      try {
        HttpClientBuilder.create().build().execute(new HttpGet(saml.getMetadataRemote()));
      } catch (IOException e) {
        p.addProblem(
            Problem.Severity.WARNING, "Cannot access remote metadata.xml file: " + e.getMessage());
      }
    }

    if (StringUtils.isEmpty(saml.getIssuerId())) {
      p.addProblem(Problem.Severity.ERROR, "No issuerId specified.");
    }

    if (StringUtils.isEmpty(saml.getKeyStore())) {
      p.addProblem(Problem.Severity.ERROR, "No keystore specified.");
    }

    if (StringUtils.isEmpty(saml.getKeyStorePassword())) {
      p.addProblem(Problem.Severity.ERROR, "No keystore password specified.");
    }

    if (StringUtils.isEmpty(saml.getKeyStoreAliasName())) {
      p.addProblem(Problem.Severity.ERROR, "No keystore alias specified.");
    }

    try {
      byte[] keyStore = validatingFileDecryptBytes(p, saml.getKeyStore());
      if (keyStore != null) {
        val keystore = KeyStore.getInstance(KeyStore.getDefaultType());

        // will throw an exception if `keyStorePassword` is invalid
        keystore.load(
            new ByteArrayInputStream(keyStore),
            secretSessionManager.decrypt(saml.getKeyStorePassword()).toCharArray());

        Collections.list(keystore.aliases()).stream()
            .filter(alias -> alias.equalsIgnoreCase(saml.getKeyStoreAliasName()))
            .findFirst()
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Keystore does not contain alias " + saml.getKeyStoreAliasName()));
      }
    } catch (Exception e) {
      p.addProblem(Problem.Severity.ERROR, "Keystore validation problem: " + e.getMessage());
    }

    if (saml.getServiceAddress() == null) {
      p.addProblem(Problem.Severity.ERROR, "No service address specified.");
    } else if (!saml.getServiceAddress().getProtocol().equalsIgnoreCase("https")) {
      p.addProblem(Problem.Severity.WARNING, "Gate should operate on HTTPS");
    }

    // Printing a warning instead of an error because Halyard doesn't depend on Gate,
    // and we don't want to prevent installing Spinnaker if new algorithms are added to Gate but not
    // to this validator
    String digest = saml.getSignatureDigest();
    if (digest != null && !KNOWN_DIGEST_ALGORITHMS.contains(digest)) {
      p.addProblem(
          Problem.Severity.WARNING,
          String.format(
              "Unrecognized SAML signatureDigest '%s'. Known algorithms are %s",
              digest, KNOWN_DIGEST_ALGORITHMS));
    }
  }
}
