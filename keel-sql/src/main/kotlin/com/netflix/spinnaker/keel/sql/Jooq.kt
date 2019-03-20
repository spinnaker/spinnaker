package com.netflix.spinnaker.keel.sql

import org.jooq.DSLContext
import org.jooq.impl.DSL

internal fun <R> DSLContext.inTransaction(fn: DSLContext.() -> R): R =
  transactionResult { tx ->
    DSL.using(tx).run(fn)
  }
