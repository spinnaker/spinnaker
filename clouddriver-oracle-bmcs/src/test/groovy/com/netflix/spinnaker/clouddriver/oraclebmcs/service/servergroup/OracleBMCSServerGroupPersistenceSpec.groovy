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
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.model.ListObjects
import com.oracle.bmc.objectstorage.model.ObjectSummary
import com.oracle.bmc.objectstorage.responses.*
import spock.lang.Specification

import java.nio.charset.Charset

class OracleBMCSServerGroupPersistenceSpec extends Specification {

  def "upsert server group"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.getCompartmentId() >> "foo"
    creds.getObjectStorageClient() >> Mock(ObjectStorageClient)
    OracleBMCSServerGroupPersistence persistence = new OracleBMCSServerGroupPersistence()
    def sg = new OracleBMCSServerGroup(name: "foo-v001", credentials: creds, launchConfig: ["compartmentId": "foo"])

    when:
    persistence.upsertServerGroup(sg)

    then:
    1 * creds.getObjectStorageClient().putObject(_) >> PutObjectResponse.builder().eTag("abc").build()
    creds.objectStorageClient.getNamespace(_) >> GetNamespaceResponse.builder().value("ns1").build()
    creds.objectStorageClient.headBucket(_) >> HeadBucketResponse.builder().eTag("abc").build()
  }

  def "delete server group"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.getCompartmentId() >> "foo"
    creds.getObjectStorageClient() >> Mock(ObjectStorageClient)
    OracleBMCSServerGroupPersistence persistence = new OracleBMCSServerGroupPersistence()
    def sg = new OracleBMCSServerGroup(name: "foo-v001", credentials: creds, launchConfig: ["compartmentId": "foo"])

    when:
    persistence.deleteServerGroup(sg)

    then:
    1 * creds.getObjectStorageClient().deleteObject(_) >> DeleteObjectResponse.builder().lastModified(new Date()).build()
    creds.objectStorageClient.getNamespace(_) >> GetNamespaceResponse.builder().value("ns1").build()
    creds.objectStorageClient.headBucket(_) >> HeadBucketResponse.builder().eTag("abc").build()
  }

  def "get server group"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.getCompartmentId() >> "foo"
    creds.getObjectStorageClient() >> Mock(ObjectStorageClient)
    OracleBMCSServerGroupPersistence persistence = new OracleBMCSServerGroupPersistence()
    def sg = new OracleBMCSServerGroup(name: "foo-v001", launchConfig: ["compartmentId": "foo"])
    def objectMapper = new ObjectMapper();
    def json = objectMapper.writeValueAsString(sg);
    def is = new ByteArrayInputStream(json.getBytes(Charset.forName("UTF-8")))
    def OracleBMCSPersistenceContext ctx = new OracleBMCSPersistenceContext(creds)
    ctx.namespace = "ns1"
    ctx.bucketChecked = true

    when:
    def serverGroupRead = persistence.getServerGroupByName(ctx, "foo-v001")

    then:
    1 * creds.getObjectStorageClient().getObject(_) >> GetObjectResponse.builder().eTag("abc").inputStream(is).build()
    serverGroupRead != null
    serverGroupRead.name == "foo-v001"
  }

  def "list server group names"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.getCompartmentId() >> "foo"
    creds.getObjectStorageClient() >> Mock(ObjectStorageClient)
    OracleBMCSServerGroupPersistence persistence = new OracleBMCSServerGroupPersistence()
    def OracleBMCSPersistenceContext ctx = new OracleBMCSPersistenceContext(creds)
    ctx.namespace = "ns1"
    ctx.bucketChecked = true

    when:
    def names = persistence.listServerGroupNames(ctx)

    then:
    1 * creds.getObjectStorageClient().listObjects(_) >> ListObjectsResponse.builder()
      .listObjects(ListObjects.builder().objects([ObjectSummary.builder().name("foo-v001").build()]).build()).build()
    names != null
    names == ["foo-v001"]
  }
}
