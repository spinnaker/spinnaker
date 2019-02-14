/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.sql.routing

import org.springframework.jdbc.datasource.lookup.DataSourceLookup
import javax.sql.DataSource

class StaticDataSourceLookup(
  /**
   * Publicly exposed as a registry of all target DataSources without digging through the Spring Environment elsewhere.
   */
  val dataSources: Map<String, DataSource>
) : DataSourceLookup {

  override fun getDataSource(dataSourceName: String): DataSource? =
    dataSources[dataSourceName]
}
