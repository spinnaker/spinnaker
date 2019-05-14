/*
 * Copyright (c) 2017, 2018, Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.artifacts.oracle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.sun.jersey.api.client.UniformInterfaceException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.UriBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OracleArtifactCredentials implements ArtifactCredentials {
  private static final String ARTIFACT_REFERENCE_PREFIX = "oci://";

  private static final String ARTIFACT_URI =
      "https://objectstorage.{arg0}.oraclecloud.com/n/{arg1}/b/{arg2}/o/{arg3}";

  @Getter private final String name;
  @Getter private final List<String> types = Collections.singletonList("oracle/object");

  private final String namespace;
  private final String region;
  private final String userId;
  private final String fingerprint;
  private final String sshPrivateKeyFilePath;
  private final String privateKeyPassphrase;
  private final String tenancyId;

  @JsonIgnore private final OracleArtifactClient client;

  OracleArtifactCredentials(String applicationName, OracleArtifactAccount account) {
    this.name = account.getName();
    this.namespace = account.getNamespace();
    this.region = account.getRegion();
    this.userId = account.getUserId();
    this.fingerprint = account.getFingerprint();
    this.sshPrivateKeyFilePath = account.getSshPrivateKeyFilePath();
    this.privateKeyPassphrase = account.getPrivateKeyPassphrase();
    this.tenancyId = account.getTenancyId();

    this.client =
        new OracleArtifactClient(
            userId, sshPrivateKeyFilePath, privateKeyPassphrase, fingerprint, tenancyId);
  }

  public InputStream download(Artifact artifact) throws IOException {
    String reference = artifact.getReference();
    if (reference.startsWith(ARTIFACT_REFERENCE_PREFIX)) {
      reference = reference.substring(ARTIFACT_REFERENCE_PREFIX.length());
    }

    int slash = reference.indexOf("/");
    if (slash <= 0) {
      throw new IllegalArgumentException(
          "Oracle references must be of the format oci://<bucket>/<file-path>, got: " + artifact);
    }

    String bucketName = reference.substring(0, slash);
    String path = reference.substring(slash + 1);

    URI uri = UriBuilder.fromPath(ARTIFACT_URI).build(region, namespace, bucketName, path);

    try {
      return client.readObject(uri);
    } catch (UniformInterfaceException e) {
      if (e.getResponse().getStatus() == 404) {
        throw new IOException("Object not found (key: " + path + ")");
      }
      throw e;
    }
  }
}
