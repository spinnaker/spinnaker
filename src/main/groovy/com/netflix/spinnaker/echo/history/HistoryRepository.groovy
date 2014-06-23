/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.history

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.MutationBatch
import com.netflix.astyanax.connectionpool.exceptions.NotFoundException
import com.netflix.astyanax.model.Column
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.model.ColumnList
import com.netflix.astyanax.serializers.StringSerializer
import com.netflix.spinnaker.echo.utils.Zip
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Repository

/**
 * Repository for history
 */
@Repository
@SuppressWarnings('PropertyName')
class HistoryRepository implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    Keyspace keyspace

    static ColumnFamily<String, String> CF_HISTORY
    static final String CF_NAME = 'history'
    ObjectMapper mapper = new ObjectMapper()

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        CF_HISTORY = ColumnFamily.newColumnFamily(CF_NAME, StringSerializer.get(), StringSerializer.get())
        List<String> columnFamilyNames = keyspace.describeKeyspace().columnFamilyList*.name
        if (!columnFamilyNames.contains(CF_HISTORY.name)) {
            keyspace.createColumnFamily(CF_HISTORY, null)
        }
    }

    List<Map> listHistory() {
        List<Map> history = []
        ColumnList<String> result = keyspace.prepareQuery(CF_HISTORY)
            .getKey(CF_NAME)
            .execute().result
        if (result != null) {
            history = result.collect { mapper.readValue(Zip.decompress(it.byteArrayValue), Map) }
        }
        history
    }

    Map getHistory(String name) {
        Map history
        try {
            Column<String> result = keyspace.prepareQuery(CF_HISTORY)
                .getKey(CF_NAME)
                .getColumn(name)
                .execute().result
            history = mapper.readValue(Zip.decompress(result.byteArrayValue), Map)
        } catch (NotFoundException ignored) {
            history = null
        }
        history
    }

    void saveHistory(String name, String history) {
        MutationBatch m = keyspace.prepareMutationBatch()
        m.withRow(CF_HISTORY, CF_NAME).putColumn(name, Zip.compress(history))
        m.execute()
    }

    void deleteHistory(String name) {
        MutationBatch m = keyspace.prepareMutationBatch()
        m.withRow(CF_HISTORY, CF_NAME).deleteColumn(name)
        m.execute()
    }

}
