/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.front50.model;

import com.google.common.base.Supplier;
import com.netflix.spinnaker.front50.config.OracleBMCSProperties;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;
import com.oracle.bmc.http.signing.DefaultRequestSigner;
import com.oracle.bmc.http.signing.RequestSigner;
import com.oracle.bmc.objectstorage.model.CreateBucketDetails;
import com.oracle.bmc.objectstorage.model.ListObjects;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.client.urlconnection.URLConnectionClientHandler;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class OracleBMCSStorageService implements StorageService {

  private final Client client;
  private final String endpoint = "https://objectstorage.{arg0}.oraclecloud.com";
  private final String region;
  private final String namespace;
  private final String compartmentId;
  private final String bucketName;


  private class RequestSigningFilter extends ClientFilter {
    private final RequestSigner signer;

    public RequestSigningFilter(RequestSigner requestSigner) {
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

  public OracleBMCSStorageService(OracleBMCSProperties oracleBMCSProperties) throws IOException {
    this.region = oracleBMCSProperties.getRegion();
    this.bucketName = oracleBMCSProperties.getBucketName();
    this.namespace = oracleBMCSProperties.getNamespace();
    this.compartmentId = oracleBMCSProperties.getCompartmentId();

    Supplier<InputStream> privateKeySupplier = new SimplePrivateKeySupplier(oracleBMCSProperties.getSshPrivateKeyFilePath());
    AuthenticationDetailsProvider provider = SimpleAuthenticationDetailsProvider.builder()
            .userId(oracleBMCSProperties.getUserId())
            .fingerprint(oracleBMCSProperties.getFingerprint())
            .privateKeySupplier(privateKeySupplier)
            .tenantId(oracleBMCSProperties.getTenancyId())
            .build();

    RequestSigner requestSigner = DefaultRequestSigner.createRequestSigner(provider);

    ClientConfig clientConfig = new DefaultClientConfig();
    client = new Client(new URLConnectionClientHandler(), clientConfig);
    client.addFilter(new OracleBMCSStorageService.RequestSigningFilter(requestSigner));
  }

  @Override
  public void ensureBucketExists() {
    WebResource wr = client.resource(UriBuilder.fromPath(endpoint + "/n/{arg1}/b/{arg2}")
            .build(region, namespace, bucketName));
    wr.accept(MediaType.APPLICATION_JSON_TYPE);
    ClientResponse rsp = wr.head();
    if (rsp.getStatus() == 404) {
      CreateBucketDetails createBucketDetails = CreateBucketDetails.builder()
              .name(bucketName)
              .compartmentId(compartmentId)
              .build();
      wr = client.resource(UriBuilder.fromPath(endpoint + "/n/{arg1}/b/")
              .build(region, namespace));
      wr.accept(MediaType.APPLICATION_JSON_TYPE);
      wr.post(createBucketDetails);
    } else if (rsp.getStatus() != 200) {
      throw new RuntimeException(rsp.toString());
    }
  }

  @Override
  public boolean supportsVersioning() {
    return false;
  }

  @Override
  public <T extends Timestamped> T loadObject(ObjectType objectType, String objectKey) throws NotFoundException {
    WebResource wr = client.resource(UriBuilder.fromPath(endpoint + "/n/{arg1}/b/{arg2}/o/{arg3}")
            .build(region, namespace, bucketName, buildOSSKey(objectType.group, objectKey, objectType.defaultMetadataFilename)));
    wr.accept(MediaType.APPLICATION_JSON_TYPE);
    try {
      T obj = (T) wr.get(objectType.clazz);
      return obj;
    } catch (UniformInterfaceException e) {
      if (e.getResponse().getStatus() == 404) {
        return null;
      }
      throw e;
    }
  }

  @Override
  public void deleteObject(ObjectType objectType, String objectKey) {
    WebResource wr = client.resource(UriBuilder.fromPath(endpoint + "/n/{arg1}/b/{arg2}/o/{arg3}")
            .build(region, namespace, bucketName, buildOSSKey(objectType.group, objectKey, objectType.defaultMetadataFilename)));
    wr.accept(MediaType.APPLICATION_JSON_TYPE);
    try {
      wr.delete();
    } catch (UniformInterfaceException e) {
      if (e.getResponse().getStatus() == 404) {
        return;
      }
      throw e;
    }

    updateLastModified(objectType);
  }

  @Override
  public <T extends Timestamped> void storeObject(ObjectType objectType, String objectKey, T item) {
    WebResource wr = client.resource(UriBuilder.fromPath(endpoint + "/n/{arg1}/b/{arg2}/o/{arg3}")
            .build(region, namespace, bucketName, buildOSSKey(objectType.group, objectKey, objectType.defaultMetadataFilename)));
    wr.accept(MediaType.APPLICATION_JSON_TYPE);
    wr.put(item);

    updateLastModified(objectType);
  }

  @Override
  public Map<String, Long> listObjectKeys(ObjectType objectType) {
    WebResource wr = client.resource(UriBuilder.fromPath(endpoint + "/n/{arg1}/b/{arg2}/o")
            .queryParam("prefix", objectType.group)
            .queryParam("fields", "name,timeCreated")
            .build(region, namespace, bucketName));
    wr.accept(MediaType.APPLICATION_JSON_TYPE);
    ListObjects listObjects = wr.get(ListObjects.class);
    Map<String, Long> results = new HashMap<>();
    for (ObjectSummary summary : listObjects.getObjects()) {
      if (summary.getName().endsWith(objectType.defaultMetadataFilename)) {
        results.put(buildObjectKey(objectType, summary.getName()), summary.getTimeCreated().getTime());
      }
    }
    return results;
  }

  @Override
  public <T extends Timestamped> Collection<T> listObjectVersions(ObjectType objectType, String objectKey, int maxResults) throws NotFoundException {
    throw new RuntimeException("OracleBMCS Storage Service does not support versioning");
  }

  @Override
  public long getLastModified(ObjectType objectType) {
    WebResource wr = client.resource(UriBuilder.fromPath(endpoint + "/n/{arg1}/b/{arg2}/o/{arg3}")
            .build(region, namespace, bucketName, objectType.group + "/last-modified.json"));
    wr.accept(MediaType.APPLICATION_JSON_TYPE);
    try {
      LastModified lastModified = wr.get(LastModified.class);
      return lastModified.getLastModified();
    } catch (Exception e) {
      return 0L;
    }
  }

  private void updateLastModified(ObjectType objectType) {
    WebResource wr = client.resource(UriBuilder.fromPath(endpoint + "/n/{arg1}/b/{arg2}/o/{arg3}")
            .build(region, namespace, bucketName, objectType.group + "/last-modified.json"));
    wr.accept(MediaType.APPLICATION_JSON_TYPE);
    wr.put(new LastModified());
  }

  private String buildOSSKey(String group, String objectKey, String metadataFilename) {
    if (objectKey.endsWith(metadataFilename)) {
      return objectKey;
    }

    return (group + "/" + objectKey.toLowerCase() + "/" + metadataFilename).replace("//", "/");
  }

  private String buildObjectKey(ObjectType objectType, String ossKey) {
    return ossKey
            .replaceAll(objectType.group + "/", "")
            .replaceAll("/" + objectType.defaultMetadataFilename, "");
  }

}
