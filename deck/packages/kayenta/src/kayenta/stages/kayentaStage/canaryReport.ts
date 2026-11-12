import { findLast, has } from 'lodash';

import type { IExecutionStage, IStage } from '@spinnaker/core';

export interface ILatestReport {
  configId: string;
  runId: string;
}

// A run has a report worth linking to once it knows which config and Kayenta execution produced it
// and it is no longer running.
export function hasViewableReport(run: IExecutionStage | IStage): boolean {
  return (
    has(run, 'context.canaryConfigId') && has(run, 'context.canaryPipelineExecutionId') && run.status !== 'RUNNING'
  );
}

// Points at the report of the last run that has finished and has a report to show, or null if none does.
export function findLatestReport(runs: Array<IExecutionStage | IStage>): ILatestReport | null {
  const latestRun = findLast(runs, hasViewableReport);
  return latestRun
    ? { configId: latestRun.context.canaryConfigId, runId: latestRun.context.canaryPipelineExecutionId }
    : null;
}
