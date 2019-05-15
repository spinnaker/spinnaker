package com.netflix.spinnaker.keel.sql

import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL

internal fun <R> DSLContext.inTransaction(fn: DSLContext.() -> R): R =
  transactionResult { tx ->
    DSL.using(tx).run(fn)
  }

internal inline fun <reified E> Record.into(): E = into(E::class.java)

internal inline fun <reified T> field(sql: String): Field<T> = DSL.field(sql, T::class.java)
