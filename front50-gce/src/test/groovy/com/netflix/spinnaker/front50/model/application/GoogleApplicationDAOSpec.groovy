/*
 * Copyright 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.model.application

import com.google.api.services.datastore.DatastoreV1.Entity
import com.google.api.services.datastore.DatastoreV1.EntityResult
import com.google.api.services.datastore.DatastoreV1.Property
import com.google.api.services.datastore.DatastoreV1.QueryResultBatch
import com.google.api.services.datastore.DatastoreV1.RunQueryResponse
import com.google.api.services.datastore.DatastoreV1.Value
import com.google.api.services.datastore.client.Datastore
import com.google.api.services.datastore.client.DatastoreFactory
import com.google.api.services.datastore.client.DatastoreOptions
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import com.netflix.spinnaker.front50.exception.NotFoundException
import groovy.mock.interceptor.MockFor
import spock.lang.Specification

class GoogleApplicationDAOSpec extends Specification {
  void 'should update an existing record'() {
    setup:
      def dao = buildDAO()
      def datastoreMock = dao.datastoreFactory.create(null)
      def attributes = [
        "group": "tst-group",
        "tags" : "[1,ok, test]"]

    when:
      dao.update("SampleApp1", attributes)

    then:
      1 * datastoreMock.beginTransaction(_)
      1 * datastoreMock.lookup(_)
      1 * datastoreMock.commit(_)
  }

  void 'should delete an item'() {
    setup:
      def dao = buildDAO()
      def datastoreMock = dao.datastoreFactory.create(null)

    when:
      dao.delete("TEST")

    then:
      1 * datastoreMock.beginTransaction(_)
      1 * datastoreMock.commit(_)
  }

  void 'should save'() {
    setup:
      def dao = buildDAO()
      def datastoreMock = dao.datastoreFactory.create(null)
      def attributes = [
        "group"      : "tst-group",
        "type"       : "test type",
        "description": "test",
        "owner"      : "Kevin McEntee",
        "email"      : "web@netflix.com",
        "updateTs"   : "1265752693581",
        "tags"       : "[1,ok, test]"]

    when:
      def application = dao.create("SampleApp1", attributes)

    then:
      application.email == 'web@netflix.com'
      application.createTs != null
      1 * datastoreMock.beginTransaction(_)
      1 * datastoreMock.lookup(_)
      1 * datastoreMock.commit(_)
  }

  void 'should throw exception if no application is found'() {
    setup:
      def dao = buildDAO()
      def datastoreMock = dao.datastoreFactory.create(null)

    when:
      dao.findByName("SAMPLEAPP")

    then:
      1 * datastoreMock.runQuery(_)

      final NotFoundException exp = thrown()
      exp.message == "No Application found by name of SAMPLEAPP in dataset null."
  }

  void 'should build search query from provided map'() {
    setup:
      def dao = buildDAO()
      def datastoreMock = buildDatastoreGroovyMock(1)
      def datastoreFactoryMock = Mock(DatastoreFactory)
      dao.datastoreFactory = datastoreFactoryMock

    when:
      def app

      datastoreMock.use {
        datastoreFactoryMock.create(_) >> new Datastore()

        app = dao.search([name:name, email:email])
      }

    then:
      thrown NotFoundException

    where:
      name = "a"
      email = "b"
  }

  void 'should be able to search case-insensitively'() {
    setup:
      def dao = buildDAO()
      def datastoreMock = buildDatastoreGroovyMock(1)
      def datastoreFactoryMock = Mock(DatastoreFactory)
      dao.datastoreFactory = datastoreFactoryMock

    when:
      datastoreMock.use {
        datastoreFactoryMock.create(_) >> new Datastore()

        dao.search([name: name.toLowerCase(), email: email])
      }

    then:
      notThrown(NotFoundException)

    when:
      def datastoreMock2 = buildDatastoreGroovyMock(1)
      def datastoreFactoryMock2 = Mock(DatastoreFactory)
      dao.datastoreFactory = datastoreFactoryMock2
      datastoreMock2.use {
        datastoreFactoryMock2.create(_) >> new Datastore()

        dao.search([name: name, email: email])
      }

    then:
      notThrown(NotFoundException)

    where:
      name = "SAMPLEAPP"
      email = "web@netflix.com"
  }

  void 'should throw exception if no applications exist'() {
    setup:
      def dao = buildDAO()
      def datastoreMock = dao.datastoreFactory.create(null)

    when:
      dao.all()

    then:
      1 * datastoreMock.runQuery(_)

      final NotFoundException exp = thrown()
      exp.message == "No Applications found in dataset null."
  }

  void 'should find one application by name'() {
    setup:
      def dao = buildDAO()
      def datastoreMock = buildDatastoreGroovyMock(1)
      def datastoreFactoryMock = Mock(DatastoreFactory)
      dao.datastoreFactory = datastoreFactoryMock

    when:
      def app

      datastoreMock.use {
        datastoreFactoryMock.create(_) >> new Datastore()

        app = dao.findByName("SAMPLEAPP")
      }

    then:
      app != null
      app.name == "SAMPLEAPP"
      app.description == "netflix.com application"
      app.owner == "Kevin McEntee"
      app.type == "Standalone Application"
  }

  void 'should find all applications'() {
    setup:
      def dao = buildDAO()
      def datastoreMock = buildDatastoreGroovyMock(2)
      def datastoreFactoryMock = Mock(DatastoreFactory)
      dao.datastoreFactory = datastoreFactoryMock

    when:
      def apps

      datastoreMock.use {
        datastoreFactoryMock.create(_) >> new Datastore()

        apps = dao.all()
      }

    then:
      apps != null
      apps.size() == 2
  }

  private GoogleApplicationDAO buildDAO() {
    def datastoreMock = Mock(Datastore)
    def datastoreFactoryMock = Mock(DatastoreFactory)
    datastoreFactoryMock.create(_) >> datastoreMock

    def datastoreOptionsBuilderMock = Mock(DatastoreOptions.Builder)
    datastoreOptionsBuilderMock.credential(_) >> datastoreOptionsBuilderMock
    datastoreOptionsBuilderMock.dataset(_) >> datastoreOptionsBuilderMock

    def credentialsMock = Mock(GoogleNamedAccountCredentials)
    credentialsMock.credentials >> Mock(GoogleCredentials)

    new GoogleApplicationDAO(datastoreFactory: datastoreFactoryMock,
                             datastoreOptionsBuilder: datastoreOptionsBuilderMock,
                             credentials: credentialsMock)
  }

  private def buildDatastoreGroovyMock(int numResults) {
    def batchBuilder = new QueryResultBatch.Builder()
    batchBuilder.setEntityResultType(EntityResult.ResultType.FULL)
    numResults.times {
      batchBuilder.addEntityResult(buildEntityResultBuilder())
    }
    batchBuilder.setMoreResults(QueryResultBatch.MoreResultsType.NO_MORE_RESULTS)

    def runQueryResponseMock = new MockFor(RunQueryResponse)
    runQueryResponseMock.demand.getBatch { batchBuilder.build() }
    def runQueryResponse = runQueryResponseMock.proxyInstance(false)

    def datastoreMock = new MockFor(Datastore)
    datastoreMock.demand.asBoolean { true }
    datastoreMock.demand.runQuery { runQueryResponse }

    return datastoreMock
  }

  private EntityResult.Builder buildEntityResultBuilder() {
    def entityBuilder = Entity.newBuilder()
    entityBuilder.addProperty(Property.newBuilder()
                                      .setName("name")
                                      .setValue(Value.newBuilder().setStringValue("SAMPLEAPP")))
    entityBuilder.addProperty(Property.newBuilder()
                                      .setName("email")
                                      .setValue(Value.newBuilder().setStringValue("web@netflix.com")))
    entityBuilder.addProperty(Property.newBuilder()
                                      .setName("createTs")
                                      .setValue(Value.newBuilder().setStringValue("1265752693581")))
    entityBuilder.addProperty(Property.newBuilder()
                                      .setName("updateTs")
                                      .setValue(Value.newBuilder().setStringValue("1265752693581")))
    entityBuilder.addProperty(Property.newBuilder()
                                      .setName("description")
                                      .setValue(Value.newBuilder().setStringValue("netflix.com application")))
    entityBuilder.addProperty(Property.newBuilder()
                                      .setName("owner")
                                      .setValue(Value.newBuilder().setStringValue("Kevin McEntee")))
    entityBuilder.addProperty(Property.newBuilder()
                                      .setName("type")
                                      .setValue(Value.newBuilder().setStringValue("Standalone Application")))

    def entityResultBuilder = EntityResult.newBuilder()
    entityResultBuilder.setEntity(entityBuilder)

    return entityResultBuilder
  }
}
