/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("elastic-search")
public class ElasticSearchConfigProperties {
  private String activeIndex;
  private String connection;

  private int readTimeout = 30000;
  private int connectionTimeout = 10000;

  // As of Elasticsearch 6.0, new indices can only contain a single mapping type. Setting singleMappingType to true
  // will index all documents under a single mapping type.  When singleMappingType is false (which is the default),
  // the mapping type of each document is set to the type of the entity being tagged.
  // The name of the unique mapping type is configurable as mappingTypeName, but is defaulted to "_doc", which is
  // recommended for forward compatibility with Elasticsearch 7.0.
  private boolean singleMappingType = false;
  private String mappingTypeName = "_doc";

  public String getActiveIndex() {
    return activeIndex;
  }

  public void setActiveIndex(String activeIndex) {
    this.activeIndex = activeIndex;
  }

  public String getConnection() {
    return connection;
  }

  public void setConnection(String connection) {
    this.connection = connection;
  }

  public int getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(int readTimeout) {
    this.readTimeout = readTimeout;
  }

  public int getConnectionTimeout() {
    return connectionTimeout;
  }

  public void setConnectionTimeout(int connectionTimeout) {
    this.connectionTimeout = connectionTimeout;
  }

  public void setSingleMappingType(boolean singleMappingType) {
    this.singleMappingType = singleMappingType;
  }

  public boolean isSingleMappingType() {
    return singleMappingType;
  }

  public void setMappingTypeName(String mappingTypeName) {
    this.mappingTypeName = mappingTypeName;
  }

  public String getMappingTypeName() {
    return mappingTypeName;
  }
}
