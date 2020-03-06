import { module } from 'angular';
import { fromPairs } from 'lodash';
import { Registry } from 'core/registry';
import { ExecutionDetailsTasks } from '../common';
import { PreconfiguredJobExecutionDetails } from './PreconfiguredJobExecutionDetails';
import { PreconfiguredJobStageConfig } from './PreconfiguredJobStageConfig';
import { PreconfiguredJobReader } from './preconfiguredJob.reader';

export const PRECONFIGUREDJOB_STAGE = 'spinnaker.core.pipeline.stage.preconfiguredJobStage';

module(PRECONFIGUREDJOB_STAGE, []).run(() => {
  PreconfiguredJobReader.list().then(preconfiguredJobs => {
    preconfiguredJobs
      .filter(job => job.uiType !== 'CUSTOM')
      .forEach(preconfiguredJob => {
        const { label, description, type, waitForCompletion, parameters, producesArtifacts } = preconfiguredJob;
        const defaults = fromPairs(parameters.filter(p => p.defaultValue).map(p => [p, name, p.defaultValue]));

        Registry.pipeline.registerStage({
          label,
          description,
          key: type,
          alias: 'preconfiguredJob',
          addAliasToConfig: true,
          restartable: true,
          defaults,
          component: PreconfiguredJobStageConfig,
          executionDetailsSections: [PreconfiguredJobExecutionDetails, ExecutionDetailsTasks],
          configuration: {
            waitForCompletion,
            parameters,
          },
          producesArtifacts,
        });
      });
  });
});
