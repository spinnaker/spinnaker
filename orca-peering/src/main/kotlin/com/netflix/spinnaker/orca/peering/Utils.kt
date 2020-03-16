package com.netflix.spinnaker.orca.peering

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL

internal fun getExecutionTable(executionType: ExecutionType): Table<Record> {
  return when (executionType) {
    ExecutionType.PIPELINE -> DSL.table("pipelines")
    ExecutionType.ORCHESTRATION -> DSL.table("orchestrations")
  }
}

internal fun getStagesTable(executionType: ExecutionType): Table<Record> {
  return when (executionType) {
    ExecutionType.PIPELINE -> DSL.table("pipeline_stages")
    ExecutionType.ORCHESTRATION -> DSL.table("orchestration_stages")
  }
}
