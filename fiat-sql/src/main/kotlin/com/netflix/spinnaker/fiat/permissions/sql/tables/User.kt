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

import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl
import org.jooq.impl.TableRecordImpl

val USER_PKEY: UniqueKey<UserTableRecord> =  Internal.createUniqueKey(UserTable.USER, "user_pkey", arrayOf(UserTable.USER.ID), true)

class UserTableRecord() : TableRecordImpl<UserTableRecord>(UserTable.USER) {

}

class UserTable(
    alias: Name,
    child: Table<out Record>?,
    path: ForeignKey<out Record, UserTableRecord>?,
    aliased: Table<UserTableRecord>?,
    parameters: Array<Field<*>?>?
) : TableImpl<UserTableRecord>(
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
        val USER = UserTable()
    }

    override fun getRecordType(): Class<UserTableRecord> = UserTableRecord::class.java

    val ID: TableField<UserTableRecord, String> = createField(DSL.name("id"), SQLDataType.VARCHAR(255).nullable(false), this, "")
    val ADMIN: TableField<UserTableRecord, Boolean> = createField(DSL.name("admin"), SQLDataType.BOOLEAN.nullable(false), this, "")
    val UPDATED_AT: TableField<UserTableRecord, Long> = createField(DSL.name("updated_at"), SQLDataType.BIGINT.nullable(false), this, "")

    private constructor(alias: Name, aliased: Table<UserTableRecord>?): this(alias, null, null, aliased, null)

    constructor(): this(DSL.name("fiat_user"), null)

    override fun getPrimaryKey(): UniqueKey<UserTableRecord> = USER_PKEY
    override fun getKeys(): List<UniqueKey<UserTableRecord>> = listOf(USER_PKEY)
    override fun `as`(alias: String): UserTable = UserTable(DSL.name(alias), this)
    override fun `as`(alias: Name): UserTable = UserTable(alias, this)
}
