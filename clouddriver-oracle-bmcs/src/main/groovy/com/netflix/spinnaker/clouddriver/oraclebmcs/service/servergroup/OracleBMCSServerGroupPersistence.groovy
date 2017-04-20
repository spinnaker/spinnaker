/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.oracle.bmc.objectstorage.model.CreateBucketDetails
import com.oracle.bmc.objectstorage.requests.*
import groovy.transform.Synchronized
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

import java.nio.charset.Charset

/**
 * Uses Object Storage as a persistent store for server group data. This is a temporary work around
 * because BMCS does not currently support server groups as a native concept.
 */
@Slf4j
@Component
class OracleBMCSServerGroupPersistence {

  private static class OracleBMCSServerGroupPersistenceException extends RuntimeException {

    OracleBMCSServerGroupPersistenceException(String var1, Throwable var2) {
      super(var1, var2)
    }

    OracleBMCSServerGroupPersistenceException(String var1) {
      super(var1)
    }
  }

  /**
   * Types of operation that must be performed in an atomic way.
   */
  private enum PersistenceOperation {

    READ, UPSERT, DELETE
  }

  /**
   * The reserved name for the bucket used to contain all server group data objects.
   */
  private final String SERVERGROUP_BUCKET_NAME = "_spinnaker_server_group_data"

  private final Charset UTF_8_CHARSET = Charset.forName("UTF-8")

  /**
   * Lists the server group names for the specified account.
   *
   * We do not consider "list" to conflict with READ, UPSERT or DELETE; this is because we only look at the names and
   * to get the actual data a client has to subsequently perform READ operations anyway.
   *
   * The intended usage by the client of this class is:
   *
   * - list the server group names;
   * - for each name, do a read and get the actual data;
   * - if someone has deleted the server group sometimes between the list and the read, the client deals with it.
   */
  protected List<String> listServerGroupNames(OracleBMCSPersistenceContext ctx) {
    def result = []
    def namespace = getStorageNamespaceFor(ctx)
    ensureStorageBucketFor(ctx)

    try {
      def rq = ListObjectsRequest.builder().namespaceName(namespace)
        .bucketName(SERVERGROUP_BUCKET_NAME)
        .build()
      def rs = ctx.creds.objectStorageClient.listObjects(rq)
      def maybeListSummaries = rs?.getListObjects()?.getObjects()
      if (maybeListSummaries != null) {
        result = maybeListSummaries.collect { it.getName() }
      }
    }
    catch (Exception e) {
      throw new OracleBMCSServerGroupPersistenceException("Failed listing contents of bucket", e)
    }

    return result
  }

  /**
   * Reads the server group data with the provided name in the provided account.
   */
  OracleBMCSServerGroup getServerGroupByName(OracleBMCSPersistenceContext ctx, String name) {
    return doPersistenceOperation(PersistenceOperation.READ, ctx, null, name)
  }

  /**
   * Writes the server group data to the persistent store; the account is inferred from the server group data.
   */
  void upsertServerGroup(OracleBMCSServerGroup sg) {
    if (sg.credentials.compartmentId == sg.launchConfig["compartmentId"] as String) {
      doPersistenceOperation(PersistenceOperation.UPSERT, new OracleBMCSPersistenceContext(sg.credentials), sg, sg.name)
    } else {
      throw new OracleBMCSServerGroupPersistenceException("Different compartments - this is not allowed")
    }
  }

  /**
   * Deletes the server group data from the persistent store; the account is inferred from the server group data.
   */
  void deleteServerGroup(OracleBMCSServerGroup sg) {
    if (sg.credentials.compartmentId == sg.launchConfig["compartmentId"] as String) {
      doPersistenceOperation(PersistenceOperation.DELETE, new OracleBMCSPersistenceContext(sg.credentials), sg, sg.name)
    } else {
      throw new OracleBMCSServerGroupPersistenceException("Different compartments - this is not allowed")
    }
  }

  @Synchronized
  private String getStorageNamespaceFor(OracleBMCSPersistenceContext ctx) {
    if (ctx.namespace) {
      return ctx.namespace
    }
    def rq = GetNamespaceRequest.builder().build()
    def rs = ctx.creds.objectStorageClient.getNamespace(rq)
    def namespace = rs?.getValue()
    if (!namespace) {
      throw new OracleBMCSServerGroupPersistenceException("Namespace not found, can't continue")
    }
    ctx.namespace = namespace
    return namespace
  }

  @Synchronized
  private ensureStorageBucketFor(OracleBMCSPersistenceContext ctx) {
    if (ctx.bucketChecked) {
      return
    }
    try {
      def rq = HeadBucketRequest.builder().namespaceName(ctx.namespace).bucketName(SERVERGROUP_BUCKET_NAME).build()
      def rs = ctx.creds.objectStorageClient.headBucket(rq)
      if (rs?.getETag()) {
        ctx.bucketChecked = true
        return
      }
      log.info("Bucket not found, will try to create...")
    } catch (Exception e) {
      log.warn("Exception when getting bucket, will try to create...", e)
    }

    try {
      def rq = CreateBucketRequest.builder().namespaceName(ctx.namespace).createBucketDetails(
        CreateBucketDetails.builder().name(SERVERGROUP_BUCKET_NAME)
          .compartmentId(ctx.creds.compartmentId)
          .build()
      ).build()
      def rs = ctx.creds.objectStorageClient.createBucket(rq)
      if (rs?.getETag()) {
        ctx.bucketChecked = true
        return
      }
    } catch (Exception e) {
      throw new OracleBMCSServerGroupPersistenceException("Failed to create bucket", e)
    }
    throw new OracleBMCSServerGroupPersistenceException("Failed to get or create bucket")
  }

  private String serverGroupToJson(OracleBMCSServerGroup sg) {
    // Save these to re-assign after ObjectMapper does its work.
    def credentials = sg.credentials
    sg.credentials = null
    def objectMapper = new ObjectMapper();
    def json = objectMapper.writeValueAsString(sg);
    sg.credentials = credentials
    return json
  }

  private OracleBMCSServerGroup jsonToServerGroup(String json, OracleBMCSNamedAccountCredentials creds) {
    def objectMapper = new ObjectMapper()
    def sg = objectMapper.readValue(json, OracleBMCSServerGroup.class)
    sg.credentials = creds
    return sg
  }

  @Synchronized
  private OracleBMCSServerGroup doPersistenceOperation(PersistenceOperation op,
                                                       OracleBMCSPersistenceContext ctx,
                                                       OracleBMCSServerGroup sg,
                                                       String name) {
    def namespace = getStorageNamespaceFor(ctx)
    ensureStorageBucketFor(ctx)
    switch (op) {
      case PersistenceOperation.READ:
        try {
          def rq = GetObjectRequest.builder().namespaceName(namespace)
            .bucketName(SERVERGROUP_BUCKET_NAME)
            .objectName(name)
            .build()
          def rs = ctx.creds.objectStorageClient.getObject(rq)
          if (!rs?.getETag()) {
            log.warn("No object to read")
            return null
          }
          def inputStream = rs.getInputStream()
          if (inputStream == null) {
            log.warn("Object empty")
            return null
          }
          String json
          inputStream.withStream { json = inputStream.getText("UTF-8") }
          sg = jsonToServerGroup(json, ctx.creds)
          return sg
        } catch (Exception e) {
          log.error("OSS Read exception", e)
          return null
        }
        break;
      case PersistenceOperation.UPSERT:
        try {
          def json = serverGroupToJson(sg)
          def rq = PutObjectRequest.builder().namespaceName(namespace)
            .bucketName(SERVERGROUP_BUCKET_NAME)
            .objectName(sg.name)
            .contentLength(json.getBytes(UTF_8_CHARSET).length)
            .putObjectBody(new ByteArrayInputStream(json.getBytes(UTF_8_CHARSET)))
            .build()
          def rs = ctx.creds.objectStorageClient.putObject(rq)
          if (!rs?.getETag()) {
            throw new OracleBMCSServerGroupPersistenceException("Upsert failed, ETag was null")
          }
          return sg
        } catch (OracleBMCSServerGroupPersistenceException e) {
          throw e
        } catch (Exception e) {
          throw new OracleBMCSServerGroupPersistenceException("Upsert failed", e)
        }
        break;
      case PersistenceOperation.DELETE:
        try {
          def rq = DeleteObjectRequest.builder().namespaceName(namespace)
            .bucketName(SERVERGROUP_BUCKET_NAME)
            .objectName(sg.name)
            .build()
          def rs = ctx.creds.objectStorageClient.deleteObject(rq)
          if (!rs?.getLastModified()) {
            throw new OracleBMCSServerGroupPersistenceException("Delete failed, lastModified was null")
          }
          return sg
        } catch (OracleBMCSServerGroupPersistenceException e) {
          throw e
        } catch (Exception e) {
          throw new OracleBMCSServerGroupPersistenceException("Delete failed", e)
        }
        break;
    }
    throw new OracleBMCSServerGroupPersistenceException("Unhandled persistence operation")
  }
}
