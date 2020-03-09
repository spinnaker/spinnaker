import { module } from 'angular';
import { IStageTypeConfig } from 'core/domain';
import { Registry } from 'core/registry';
import { ExecutionDetailsTasks } from '../common';
import { IPreconfiguredJob, PreconfiguredJobReader } from './preconfiguredJob.reader';
import { PreconfiguredJobExecutionDetails } from './PreconfiguredJobExecutionDetails';
import { PreconfiguredJobStageConfig } from './PreconfiguredJobStageConfig';

export function buildPreconfiguredJobStage(job: IPreconfiguredJob): IStageTypeConfig {
  const { label, description, type, waitForCompletion, parameters, producesArtifacts } = job;

  return {
    label,
    description,
    key: type,
    alias: 'preconfiguredJob',
    addAliasToConfig: true,
    restartable: true,
    // Overriden when the
    defaults: { parameters: {} },
    component: PreconfiguredJobStageConfig,
    executionDetailsSections: [PreconfiguredJobExecutionDetails, ExecutionDetailsTasks],
    configuration: {
      waitForCompletion,
      parameters,
    },
    producesArtifacts,
  };
}

export const PRECONFIGUREDJOB_STAGE = 'spinnaker.core.pipeline.stage.preconfiguredJobStage';

module(PRECONFIGUREDJOB_STAGE, []).run(() => {
  PreconfiguredJobReader.list().then(preconfiguredJobs => {
    const basicPreconfiguredJobs = preconfiguredJobs.filter(job => job.uiType !== 'CUSTOM');
    return Promise.all(
      basicPreconfiguredJobs.map(job => {
        const stageConfig = buildPreconfiguredJobStage(job);
        return Registry.pipeline.registerPreconfiguredJobStage(stageConfig);
      }),
    );
  });
});
