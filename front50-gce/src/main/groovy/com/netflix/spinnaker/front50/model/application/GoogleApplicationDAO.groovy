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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.services.datastore.client.Datastore
import com.google.api.services.datastore.client.DatastoreException
import com.google.api.services.datastore.client.DatastoreFactory
import com.google.api.services.datastore.client.DatastoreOptions
import com.google.api.services.datastore.DatastoreV1.BeginTransactionRequest
import com.google.api.services.datastore.DatastoreV1.BeginTransactionResponse
import com.google.api.services.datastore.DatastoreV1.CommitRequest
import com.google.api.services.datastore.DatastoreV1.Entity
import com.google.api.services.datastore.DatastoreV1.EntityResult
import com.google.api.services.datastore.DatastoreV1.GqlQuery
import com.google.api.services.datastore.DatastoreV1.Key
import com.google.api.services.datastore.DatastoreV1.LookupRequest
import com.google.api.services.datastore.DatastoreV1.LookupResponse
import com.google.api.services.datastore.DatastoreV1.Property
import com.google.api.services.datastore.DatastoreV1.RunQueryRequest
import com.google.api.services.datastore.DatastoreV1.Value
import com.google.protobuf.ByteString
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import com.netflix.spinnaker.front50.exception.NotFoundException
import groovy.transform.Canonical

@Canonical
class GoogleApplicationDAO implements ApplicationDAO {
  private static final String NAMESPACE = "Spinnaker"
  private static final String KIND = "Application"
  protected DatastoreFactory datastoreFactory
  protected DatastoreOptions.Builder datastoreOptionsBuilder
  protected GoogleNamedAccountCredentials credentials
  protected EntityToApplicationConverter entityToApplicationConverter

  @Override
  boolean isHealthly() {
    try {
      credentials?.credentials != null && createDatastoreConnection(credentials.projectName,
                                                                    datastoreFactory,
                                                                    datastoreOptionsBuilder,
                                                                    credentials)
    } catch (NotFoundException e) {
      false
    }
  }

  @Override
  Set<Application> search(Map<String, String> attributes) {
    def items = all().findAll { app ->
      def result = true
      attributes.each { k, v ->
        // If the property is not specified, or it exists but its associated value doesn't match, it's a miss.
        if (!app.hasProperty(k)
            || (app.hasProperty(k) && (!app[k] || ((String)app[k]).toLowerCase() != v?.toLowerCase()))) {
          result = false
        }
      }
      result
    } as Set

    if (!items) {
      throw new NotFoundException("No Applications found for search criteria $attributes in dataset $credentials.projectName.")
    }

    items
  }

  @Override
  Application create(String id, Map<String, String> properties) {
    properties['createTs'] = System.currentTimeMillis() as String

    createOrUpdate(id, properties, datastoreFactory, datastoreOptionsBuilder, credentials)
  }

  @Override
  void update(String id, Map<String, String> properties) {
    properties['updateTs'] = System.currentTimeMillis() as String

    createOrUpdate(id, properties, datastoreFactory, datastoreOptionsBuilder, credentials)
  }

  @Override
  void delete(String id) {
    String datasetId = credentials.projectName;
    Datastore datastore = createDatastoreConnection(datasetId, datastoreFactory, datastoreOptionsBuilder, credentials)

    try {
      // Create the transaction.
      BeginTransactionRequest.Builder treq = BeginTransactionRequest.newBuilder();
      BeginTransactionResponse tres = datastore.beginTransaction(treq.build());
      ByteString tx = tres?.getTransaction();

      // Create the key.
      Key.Builder key = Key.newBuilder().addPathElement(Key.PathElement.newBuilder().setKind(KIND)
                                                                                    .setName(id.toUpperCase()));
      key.getPartitionIdBuilder().setNamespace(NAMESPACE);

      // Create the commit request and associate it with the transaction.
      CommitRequest.Builder creq = CommitRequest.newBuilder();
      if (tx) {
        creq.setTransaction(tx);
      }
      creq.getMutationBuilder().addDelete(key);

      // Delete the Application and close the transaction.
      datastore.commit(creq.build());
    } catch (DatastoreException exception) {
      throw new IllegalArgumentException("Unable to delete Application $id from $datasetId: ${exception.message}.")
    }
  }

  @Override
  Application findByName(String name) throws NotFoundException {
    def datasetId = credentials.projectName
    // TODO(duftler): Retrieve this by key instead? I think this reads cleaner. Revisit.
    def entities = query(datasetId,
                         "select * from Application where name = @name",
                         [name: name.toLowerCase()],
                         datastoreFactory,
                         datastoreOptionsBuilder,
                         credentials)

    if (entities?.size() > 0) {
      return entityToApplicationConverter.mapToApp(entities[0].getEntity())
    } else {
      throw new NotFoundException("No Application found by name of $name in dataset $datasetId.")
    }
  }

  @Override
  Set<Application> all() throws NotFoundException {
    def datasetId = credentials.projectName
    def entities = query(datasetId,
                         "select * from Application limit @limit",
                         [limit: 2500],
                         datastoreFactory,
                         datastoreOptionsBuilder,
                         credentials)

    if (entities?.size() > 0) {
      return entities.collect { entityToApplicationConverter.mapToApp(it.entity) }
    } else {
      throw new NotFoundException("No Applications found in dataset $datasetId.")
    }
  }

  private Application createOrUpdate(String id,
                                     Map<String, String> properties,
                                     DatastoreFactory datastoreFactory,
                                     DatastoreOptions.Builder datastoreOptionsBuilder,
                                     GoogleNamedAccountCredentials credentials) {
    String datasetId = credentials.projectName
    Datastore datastore = createDatastoreConnection(datasetId, datastoreFactory, datastoreOptionsBuilder, credentials)

    try {
      // Create the transaction.
      BeginTransactionRequest.Builder treq = BeginTransactionRequest.newBuilder()
      BeginTransactionResponse tres = datastore.beginTransaction(treq.build())
      ByteString tx = tres?.getTransaction()

      // Create the lookup request.
      LookupRequest.Builder lreq = LookupRequest.newBuilder()
      Key.Builder key = Key.newBuilder().addPathElement(Key.PathElement.newBuilder().setKind(KIND)
                                                                                    .setName(id))
      key.getPartitionIdBuilder().setNamespace(NAMESPACE);
      lreq.addKey(key)
      if (tx) {
        lreq.getReadOptionsBuilder().setTransaction(tx)
      }

      // Execute the lookup request.
      LookupResponse lresp = datastore.lookup(lreq.build())

      // Create the commit request and associate it with the transaction.
      CommitRequest.Builder creq = CommitRequest.newBuilder()
      if (tx) {
        creq.setTransaction(tx)
      }

      def entityBuilder

      if (lresp?.getFoundCount() > 0) {
        // If the Application entity was found, update it.
        entityBuilder = lresp.foundList[0].entity.toBuilder()
      } else {
        // Otherwise, create it from scratch.
        entityBuilder = Entity.newBuilder()
        entityBuilder.setKey(key)
      }

      def entity

      // Iterate over each of the properties passed in.
      properties.each { newProperty ->
        // The application name is lowercased prior to a lookup, so we must lowercase it on creation as well.
        if (newProperty.key == "name") {
          newProperty.value = newProperty.value.toLowerCase()
        }

        // And check if each exists already.
        def existingPropertyBuilder = entityBuilder.propertyBuilderList.find {
          existingPropertyBuilder -> existingPropertyBuilder.name == newProperty.key
        }

        if (existingPropertyBuilder) {
          // If it exists, update its value.
          existingPropertyBuilder.setValue(Value.newBuilder().setStringValue(newProperty.value))
        } else {
          // Otherwise, add the new property.
          entityBuilder.addProperty(Property.newBuilder()
                                            .setName(newProperty.key)
                                            .setValue(Value.newBuilder().setStringValue(newProperty.value)))
        }

        entity = entityBuilder.build()

        // Insert the entity in the commit request mutation.
        if (lresp?.getFoundCount() > 0) {
          creq.getMutationBuilder().addUpdate(entity)
        } else {
          creq.getMutationBuilder().addInsert(entity)
        }
      }

      // Create/update the Application and close the transaction.
      datastore.commit(creq.build())

      // Return the application, whether it already existed or was newly created.
      Application application = entityToApplicationConverter.mapToApp(entity)
      application.name = id
      return application
    } catch (DatastoreException exception) {
      throw new IllegalArgumentException("Unable to update Application $id in $datasetId: ${exception.message}.")
    }
  }

  private static List<EntityResult> query(String datasetId,
                                          String queryString,
                                          Map<String, String> queryArgs,
                                          DatastoreFactory datastoreFactory,
                                          DatastoreOptions.Builder datastoreOptionsBuilder,
                                          GoogleNamedAccountCredentials credentials) throws NotFoundException {
    try {
      Datastore datastore = createDatastoreConnection(datasetId, datastoreFactory, datastoreOptionsBuilder, credentials)

      GqlQuery.Builder queryBuilder = GqlQuery.newBuilder().setQueryString(queryString)
      queryArgs.each {
        def valueBuilder = it.value instanceof Integer
                           ? new Value.Builder().setIntegerValue(it.value)
                           : new Value.Builder().setStringValue(it.value)
        queryBuilder.addNameArgBuilder().setName(it.key).setValue(valueBuilder)
      }

      def requestBuilder = RunQueryRequest.newBuilder().setGqlQuery(queryBuilder)
      requestBuilder.getPartitionIdBuilder().setNamespace(NAMESPACE);
      def response = datastore.runQuery(requestBuilder.build());

      response?.getBatch()?.getEntityResultList()
    } catch (DatastoreException exception) {
      throw new NotFoundException("Unable to execute query on $datasetId: ${exception.message}.")
    }
  }

  // This method will not return null.
  private static Datastore createDatastoreConnection(String datasetId,
                                                     DatastoreFactory datastoreFactory,
                                                     DatastoreOptions.Builder datastoreOptionsBuilder,
                                                     GoogleNamedAccountCredentials credentials) throws NotFoundException {
    def datastore

    try {
      GoogleCredential.Builder credentialBuilder =
              credentials.credentials.createCredentialBuilder("https://www.googleapis.com/auth/datastore",
                                                              "https://www.googleapis.com/auth/userinfo.email")
      datastore = datastoreFactory.create(
              datastoreOptionsBuilder.credential(credentialBuilder?.build()).dataset(datasetId).build());
    } catch (Exception exception) {
      // Don't love this, but it's in keeping with the interface and keeps the calling code a bit neater.
      throw new NotFoundException("Unable to establish connection to datastore $datasetId: ${exception.message}.")
    }

    // It's not entirely clear to me from the javadoc for DatastoreFactory.create() that the return value can never be
    // null.
    if (datastore) {
      datastore
    } else {
      throw new NotFoundException("Unable to establish connection to datastore $datasetId.")
    }
  }
}
