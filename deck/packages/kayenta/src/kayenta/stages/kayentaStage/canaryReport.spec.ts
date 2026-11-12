import type { IExecutionStage } from '@spinnaker/core';

import { findLatestReport, hasViewableReport } from './canaryReport';

const run = (status: string, context: any = { canaryConfigId: 'cfg', canaryPipelineExecutionId: 'exec' }) =>
  (({ id: `run-${status}`, status, context } as any) as IExecutionStage);

describe('hasViewableReport', () => {
  it('is false when the canary config id is missing', () => {
    expect(hasViewableReport(run('SUCCEEDED', { canaryPipelineExecutionId: 'exec' }))).toBe(false);
  });

  it('is false when the canary pipeline execution id is missing', () => {
    expect(hasViewableReport(run('SUCCEEDED', { canaryConfigId: 'cfg' }))).toBe(false);
  });

  it('is false when there is no context', () => {
    // run() would apply its default context for undefined, so build the run by hand
    expect(hasViewableReport({ id: 'x', status: 'SUCCEEDED' } as any)).toBe(false);
    expect(hasViewableReport(run('SUCCEEDED', null))).toBe(false);
  });

  it('is false while the run is RUNNING', () => {
    expect(hasViewableReport(run('RUNNING'))).toBe(false);
  });

  it.each(['SUCCEEDED', 'FAILED', 'CANCELED'])('is true for a %s run with both ids', (status) => {
    expect(hasViewableReport(run(status))).toBe(true);
  });
});

describe('findLatestReport', () => {
  const withIds = (status: string, n: number) =>
    run(status, { canaryConfigId: `cfg${n}`, canaryPipelineExecutionId: `exec${n}` });

  it('picks the last eligible run', () => {
    const runs = [withIds('SUCCEEDED', 1), withIds('FAILED', 2), withIds('SUCCEEDED', 3)];
    expect(findLatestReport(runs)).toEqual({ configId: 'cfg3', runId: 'exec3' });
  });

  it('skips a trailing RUNNING run', () => {
    const runs = [withIds('SUCCEEDED', 1), withIds('SUCCEEDED', 2), withIds('RUNNING', 3)];
    expect(findLatestReport(runs)).toEqual({ configId: 'cfg2', runId: 'exec2' });
  });

  it('skips a trailing run that has no ids yet', () => {
    const runs = [withIds('SUCCEEDED', 1), run('SUCCEEDED', {})];
    expect(findLatestReport(runs)).toEqual({ configId: 'cfg1', runId: 'exec1' });
  });

  it('returns null when no run is eligible', () => {
    expect(findLatestReport([withIds('RUNNING', 1), run('SUCCEEDED', {})])).toBeNull();
  });

  it('returns null for an empty or undefined list', () => {
    expect(findLatestReport([])).toBeNull();
    expect(findLatestReport(undefined)).toBeNull();
  });
});
