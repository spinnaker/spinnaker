import { module } from 'angular';

import {
  Registry,
  ExecutionDetailsTasks,
  IPipeline,
  IStage,
  IValidatorConfig,
  IStageOrTriggerTypeConfig,
  ICustomValidator,
} from '@spinnaker/core';

import { KubernetesV2RunJobStageConfig } from './KubernetesV2RunJobStageConfig';
import { RunJobExecutionDetails } from './RunJobExecutionDetails';

export const KUBERNETES_V2_RUN_JOB_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.runJob';

module(KUBERNETES_V2_RUN_JOB_STAGE, [])
  .config(() => {
    Registry.pipeline.registerStage({
      label: 'Run Job (Manifest)',
      description: 'Run a Kubernetes Job mainfest yaml/json file.',
      key: 'runJobManifest',
      alias: 'runJob',
      addAliasToConfig: true,
      cloudProvider: 'kubernetes',
      component: KubernetesV2RunJobStageConfig,
      executionDetailsSections: [ExecutionDetailsTasks, RunJobExecutionDetails],
      defaultTimeoutMs: 30 * 60 * 1000,
      validators: [
        {
          type: 'custom',
          validate: (
            _pipeline: IPipeline,
            stage: IStage,
            _validator: IValidatorConfig,
            _config: IStageOrTriggerTypeConfig,
          ): string => {
            if (!stage.manifest || !stage.manifest.kind) {
              return '';
            }
            if (stage.manifest.kind !== 'Job') {
              return 'Run Job (Manifest) only accepts manifest of kind Job.';
            }
            return '';
          },
        } as ICustomValidator,
      ],
    });
  })
  .controller('KubernetesV2RunJobStageConfig', KubernetesV2RunJobStageConfig);
