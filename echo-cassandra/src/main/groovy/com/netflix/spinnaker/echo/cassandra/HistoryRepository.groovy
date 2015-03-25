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

package com.netflix.spinnaker.echo.cassandra

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.MutationBatch
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.model.ColumnList
import com.netflix.astyanax.serializers.StringSerializer
import com.netflix.spinnaker.echo.utils.Zip
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Repository

import java.text.SimpleDateFormat

/**
 * Repository for history
 */
@Repository
@SuppressWarnings('PropertyName')
class HistoryRepository implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    Keyspace keyspace

    static ColumnFamily<String, String> CF_HISTORY
    static final String CF_NAME = 'eventslog'
    ObjectMapper mapper = new ObjectMapper()

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        CF_HISTORY = ColumnFamily.newColumnFamily(CF_NAME, StringSerializer.get(), StringSerializer.get())
        List<String> columnFamilyNames = keyspace.describeKeyspace().columnFamilyList*.name
        if (!columnFamilyNames.contains(CF_HISTORY.name)) {
            keyspace.createColumnFamily(CF_HISTORY, null)
        }
    }

    void saveHistory(String name, String history) {
        MutationBatch m = keyspace.prepareMutationBatch()
        String dateString = new SimpleDateFormat('yyyyMMdd').format(new Date())
        m.withRow(CF_HISTORY, CF_NAME + '_' + dateString).putColumn(name, Zip.compress(history))
        m.execute()
    }

    List<Map> get(date) {
        List<Map> history = []
        ColumnList<String> result = keyspace.prepareQuery(CF_HISTORY)
            .getKey("${CF_NAME}_${date}")
            .execute().result
        if (result != null) {
            result.each {
                try {
                    history << mapper.readValue(Zip.decompress(it.byteArrayValue), Map)
                } catch (Exception e) {
                }
            }
        }
        history
    }
}
