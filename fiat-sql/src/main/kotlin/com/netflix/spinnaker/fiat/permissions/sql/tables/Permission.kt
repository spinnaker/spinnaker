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

package com.netflix.spinnaker.fiat.permissions.sql.tables

import com.netflix.spinnaker.fiat.model.resources.ResourceType
import com.netflix.spinnaker.fiat.permissions.sql.converters.ResourceTypeConverter
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl
import org.jooq.impl.TableRecordImpl

val PERMISSION_PKEY: UniqueKey<PermissionTableRecord> =  Internal.createUniqueKey(PermissionTable.PERMISSION, "permission_pkey", arrayOf(PermissionTable.PERMISSION.USER_ID, PermissionTable.PERMISSION.RESOURCE_TYPE, PermissionTable.PERMISSION.RESOURCE_NAME), true)

class PermissionTableRecord() : TableRecordImpl<PermissionTableRecord>(PermissionTable.PERMISSION) {

}

class PermissionTable(
    alias: Name,
    child: Table<out Record>?,
    path: ForeignKey<out Record, PermissionTableRecord>?,
    aliased: Table<PermissionTableRecord>?,
    parameters: Array<Field<*>?>?
) : TableImpl<PermissionTableRecord>(
    alias,
    null,
    child,
    path,
    aliased,
    parameters,
    DSL.comment(""),
    TableOptions.table()
) {

    companion object {
        val PERMISSION = PermissionTable()
    }

    override fun getRecordType(): Class<PermissionTableRecord> = PermissionTableRecord::class.java

    val USER_ID: TableField<PermissionTableRecord, String> = createField(DSL.name("fiat_user_id"), SQLDataType.VARCHAR(255).nullable(false), this, "")
    val RESOURCE_TYPE: TableField<PermissionTableRecord, ResourceType> = createField(DSL.name("resource_type"), SQLDataType.VARCHAR(255).nullable(false), this, "", ResourceTypeConverter())
    val RESOURCE_NAME: TableField<PermissionTableRecord, String> = createField(DSL.name("resource_name"), SQLDataType.VARCHAR(255).nullable(false), this, "")

    private constructor(alias: Name, aliased: Table<PermissionTableRecord>?): this(alias, null, null, aliased, null)

    constructor(): this(DSL.name("fiat_permission"), null)

    override fun getPrimaryKey(): UniqueKey<PermissionTableRecord> = PERMISSION_PKEY
    override fun getKeys(): List<UniqueKey<PermissionTableRecord>> = listOf(PERMISSION_PKEY)
    override fun `as`(alias: String): PermissionTable = PermissionTable(DSL.name(alias), this)
    override fun `as`(alias: Name): PermissionTable = PermissionTable(alias, this)
}
