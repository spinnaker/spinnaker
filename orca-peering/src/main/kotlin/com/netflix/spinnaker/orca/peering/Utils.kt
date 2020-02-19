package com.netflix.spinnaker.orca.peering

import com.netflix.spinnaker.orca.pipeline.model.Execution
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL

internal fun getExecutionTable(executionType: Execution.ExecutionType): Table<Record> {
  return when (executionType) {
    Execution.ExecutionType.PIPELINE -> DSL.table("pipelines")
    Execution.ExecutionType.ORCHESTRATION -> DSL.table("orchestrations")
  }
}

internal fun getStagesTable(executionType: Execution.ExecutionType): Table<Record> {
  return when (executionType) {
    Execution.ExecutionType.PIPELINE -> DSL.table("pipeline_stages")
    Execution.ExecutionType.ORCHESTRATION -> DSL.table("orchestration_stages")
  }
}
