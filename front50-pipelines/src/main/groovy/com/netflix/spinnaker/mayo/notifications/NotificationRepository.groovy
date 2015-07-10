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

package com.netflix.spinnaker.mayo.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.MutationBatch
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException
import com.netflix.astyanax.connectionpool.exceptions.NotFoundException
import com.netflix.astyanax.model.Column
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.model.ColumnList
import com.netflix.astyanax.query.RowQuery
import com.netflix.astyanax.serializers.StringSerializer
import com.netflix.astyanax.util.RangeBuilder
import com.netflix.spinnaker.mayo.HierarchicalLevel
import com.netflix.spinnaker.mayo.utils.Zip
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Repository

/**
 * Repository for presets
 */
@Repository
@SuppressWarnings('PropertyName')
class NotificationRepository implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    Keyspace keyspace

    static ColumnFamily<String, String> CF_NOTIFICATIONS
    static final String CF_NAME = 'notifications'
    ObjectMapper mapper = new ObjectMapper()

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        CF_NOTIFICATIONS = ColumnFamily.newColumnFamily(CF_NAME, StringSerializer.get(), StringSerializer.get())
        List<String> columnFamilyNames = keyspace.describeKeyspace().columnFamilyList*.name
        if (!columnFamilyNames.contains(CF_NOTIFICATIONS.name)) {
            keyspace.createColumnFamily(CF_NOTIFICATIONS, null)
        }
    }

    List<Map> list() {
        List<Map> notifications = []
        ColumnList<String> columns
        try {
            RowQuery<String, String> query = keyspace.prepareQuery(CF_NOTIFICATIONS)
                .getKey(CF_NAME)
                .autoPaginate(true)
                .withColumnRange(new RangeBuilder().setLimit(5).build());

            while (!(columns = query.execute().getResult()).isEmpty()) {
                for (Column<String> c : columns) {
                    notifications << mapper.readValue(Zip.decompress(c.byteArrayValue), Map)
                }
            }
        } catch (ConnectionException e) {
        }
        notifications
    }

    Map getGlobal() {
        get(HierarchicalLevel.GLOBAL, '')
    }

    Map get(HierarchicalLevel level, String name) {
        Map notification
        try {
            Column<String> result = keyspace.prepareQuery(CF_NOTIFICATIONS)
                .getKey(CF_NAME)
                .getColumn("$level:$name".toString())
                .execute().result
            notification = mapper.readValue(Zip.decompress(result.byteArrayValue), Map)
        } catch (NotFoundException ignored) {
            notification = [email: []]
        }
        if (level == HierarchicalLevel.APPLICATION) {
            ['sms', 'email', 'hipchat'].each {
                if (getGlobal()."$it") {
                    if (!notification."${it}") {
                        notification."${it}" = []
                    }
                    notification."$it".addAll(getGlobal()."$it")
                }
            }
        }
        notification
    }

    void saveGlobal(Map notification) {
        save(HierarchicalLevel.GLOBAL, '', notification)
    }

    void save(HierarchicalLevel level, String name, Map notification) {
        ['sms', 'email', 'hipchat'].each {
            if (notification."$it"?.size()) {
                notification."$it".each { it.level = level.toString().toLowerCase() }
            }
        }
        MutationBatch m = keyspace.prepareMutationBatch()
        m.withRow(CF_NOTIFICATIONS, CF_NAME)
            .putColumn(
            "$level:$name".toString(),
            Zip.compress(mapper.writeValueAsString(notification))
        )
        m.execute()
    }

    void delete(HierarchicalLevel level, String name) {
        MutationBatch m = keyspace.prepareMutationBatch()
        m.withRow(CF_NOTIFICATIONS, CF_NAME).deleteColumn("$level:$name".toString())
        m.execute()
    }

}
