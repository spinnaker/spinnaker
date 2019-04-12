import { module } from 'angular';

import { API } from 'core/api/ApiService';
import { Registry } from 'core/registry';

import { ExecutionDetailsTasks } from '../common';
import { PreconfiguredJobExecutionDetails } from './PreconfiguredJobExecutionDetails';
import { PreconfiguredJobStageConfig } from './PreconfiguredJobStageConfig';

export interface IPreconfiguredJobParameter {
  name: string;
  label: string;
  description?: string;
  type: string;
  defaultValue?: string;
}

interface IPreconfiguredJob {
  type: string;
  label: string;
  noUserConfigurableFields: boolean;
  description?: string;
  waitForCompletion?: boolean;
  parameters?: IPreconfiguredJobParameter[];
  producesArtifacts: boolean;
}

export const PRECONFIGUREDJOB_STAGE = 'spinnaker.core.pipeline.stage.preconfiguredJobStage';

module(PRECONFIGUREDJOB_STAGE, []).run(() => {
  API.one('jobs')
    .all('preconfigured')
    .getList()
    .then((preconfiguredJobs: IPreconfiguredJob[]) => {
      preconfiguredJobs.forEach(preconfiguredJob => {
        const { label, description, type, waitForCompletion, parameters, producesArtifacts } = preconfiguredJob;
        const defaults = {
          parameters: parameters.reduce(
            (acc, parameter) => {
              if (parameter.defaultValue) {
                acc[parameter.name] = parameter.defaultValue;
              }
              return acc;
            },
            {} as any,
          ),
        };
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
