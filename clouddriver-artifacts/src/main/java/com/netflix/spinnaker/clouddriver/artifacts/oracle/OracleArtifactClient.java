/*
 * Copyright (c) 2017, 2018, Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.artifacts.oracle;

import com.google.common.base.Supplier;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;
import com.oracle.bmc.http.signing.DefaultRequestSigner;
import com.oracle.bmc.http.signing.RequestSigner;
import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.client.urlconnection.URLConnectionClientHandler;

import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class OracleArtifactClient {

  private final Client client;

  OracleArtifactClient(String userId, String sshPrivateKeyFilePath, String privateKeyPassphrase, String fingerprint, String tenancyId) {
    Supplier<InputStream> privateKeySupplier = new SimplePrivateKeySupplier(sshPrivateKeyFilePath);
    AuthenticationDetailsProvider provider = SimpleAuthenticationDetailsProvider.builder()
            .userId(userId)
            .fingerprint(fingerprint)
            .privateKeySupplier(privateKeySupplier)
            .passPhrase(privateKeyPassphrase)
            .tenantId(tenancyId)
            .build();

    RequestSigner requestSigner = DefaultRequestSigner.createRequestSigner(provider);

    ClientConfig clientConfig = new DefaultClientConfig();
    client = new Client(new URLConnectionClientHandler(), clientConfig);
    client.addFilter(new RequestSigningFilter(requestSigner));
  }

  InputStream readObject(URI uri) {
    WebResource wr = client.resource(uri);
    wr.accept(MediaType.APPLICATION_OCTET_STREAM_TYPE);
    return wr.get(InputStream.class);
  }

  private class RequestSigningFilter extends ClientFilter {
    private final RequestSigner signer;

    private RequestSigningFilter(RequestSigner requestSigner) {
      this.signer = requestSigner;
    }

    @Override
    public ClientResponse handle(ClientRequest cr) throws ClientHandlerException {
      Map<String, List<String>> stringHeaders = new HashMap<>();
      for (String key : cr.getHeaders().keySet()) {
        List<String> vals = new ArrayList<>();
        for (Object val : cr.getHeaders().get(key)) {
          vals.add((String) val);
        }
        stringHeaders.put(key, vals);
      }

      Map<String, String> signedHeaders = signer.signRequest(cr.getURI(), cr.getMethod(), stringHeaders, cr.getEntity());
      for (String key : signedHeaders.keySet()) {
        cr.getHeaders().putSingle(key, signedHeaders.get(key));
      }

      return getNext().handle(cr);
    }
  }
}
