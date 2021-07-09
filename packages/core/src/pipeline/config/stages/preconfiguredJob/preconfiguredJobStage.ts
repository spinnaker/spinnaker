import { module } from 'angular';

import { PreconfiguredJobExecutionDetails } from './PreconfiguredJobExecutionDetails';
import { PreconfiguredJobStageConfig } from './PreconfiguredJobStageConfig';
import { ExecutionDetailsTasks } from '../common';
import { IStageTypeConfig } from '../../../../domain';
import { PreconfiguredJobReader } from './preconfiguredJob.reader';
import { Registry } from '../../../../registry';

/**
 * Builds a skeleton preconfigured job stage
 *
 * After building a skeleton, register it with PipelineRegistry.registerPreconfiguredJobStage().
 * The skeleton will be filled in with details pulled from gate via the /jobs/preconfigured endpoint.
 *
 * @param preconfiguredJobKey the preconfigured job's 'type' (registered in orca)
 */
export function makePreconfiguredJobStage(preconfiguredJobType: string): IStageTypeConfig {
  return {
    label: '',
    description: '',
    key: preconfiguredJobType,
    alias: 'preconfiguredJob',
    addAliasToConfig: true,
    restartable: true,
    // Overriden with parameter metadata from /jobs/preconfigured
    defaults: { parameters: {} },
    component: PreconfiguredJobStageConfig,
    executionDetailsSections: [PreconfiguredJobExecutionDetails, ExecutionDetailsTasks],
    configuration: {
      waitForCompletion: true,
      parameters: [],
    },
    producesArtifacts: false,
  };
}

export const PRECONFIGUREDJOB_STAGE = 'spinnaker.core.pipeline.stage.preconfiguredJobStage';

module(PRECONFIGUREDJOB_STAGE, []).run(() => {
  PreconfiguredJobReader.list().then((preconfiguredJobs) => {
    const basicPreconfiguredJobs = preconfiguredJobs.filter((job) => job.uiType !== 'CUSTOM');
    return Promise.all(
      basicPreconfiguredJobs.map((job) => {
        const stageConfig = makePreconfiguredJobStage(job.type);
        return Registry.pipeline.registerPreconfiguredJobStage(stageConfig);
      }),
    );
  });
});
