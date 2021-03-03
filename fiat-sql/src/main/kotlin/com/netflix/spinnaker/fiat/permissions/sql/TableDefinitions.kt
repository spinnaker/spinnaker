/*
 * Copyright 2021 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.permissions.sql


import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import java.time.Instant

open class Table {
    companion object {
        val USER = table("fiat_user")
        val PERMISSION = table("fiat_permission")
    }
}

open class User {
    companion object {
        val ID = field("id", String::class.java)
        val ADMIN = field("admin", Boolean::class.java)
        val UPDATED_AT = field("updated_at", Long::class.java)
    }
}

open class Permission {
    companion object {
        val RESOURCE_NAME = field("resource_name", String::class.java)
        val RESOURCE_TYPE = field("resource_type", String::class.java)
        val BODY = field("body", String::class.java)
        val USER_ID = field("fiat_user_id", String::class.java)
    }

}