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

package com.netflix.spinnaker.fiat.permissions.sql.converters

import com.netflix.spinnaker.fiat.model.resources.ResourceType
import org.jooq.impl.AbstractConverter
import java.lang.IllegalArgumentException

class ResourceTypeConverter : AbstractConverter<String, ResourceType>(String::class.java, ResourceType::class.java) {

    override fun from(databaseObject: String?): ResourceType? {
        return when (databaseObject) {
            null -> null
            "" -> throw IllegalArgumentException("resource type name cannot be empty")
            "${ResourceType.ACCOUNT}" -> ResourceType.ACCOUNT
            "${ResourceType.APPLICATION}" -> ResourceType.APPLICATION
            "${ResourceType.BUILD_SERVICE}" -> ResourceType.BUILD_SERVICE
            "${ResourceType.ROLE}" -> ResourceType.ROLE
            "${ResourceType.SERVICE_ACCOUNT}" -> ResourceType.SERVICE_ACCOUNT
            else -> ResourceType(databaseObject)
        }
    }

    override fun to(userObject: ResourceType?): String? {
        if (userObject == null) return null
        if (userObject.name == "") throw IllegalArgumentException("resource type name cannot be empty")
        return userObject.name.toLowerCase()
    }

}