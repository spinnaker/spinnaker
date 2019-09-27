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

Registry.pipeline.registerStage({
  label: 'Run Job (Manifest)',
  description: 'Run a Kubernetes Job manifest yaml/json file.',
  key: 'runJobManifest',
  alias: 'runJob',
  addAliasToConfig: true,
  cloudProvider: 'kubernetes',
  component: KubernetesV2RunJobStageConfig,
  executionDetailsSections: [RunJobExecutionDetails, ExecutionDetailsTasks],
  supportsCustomTimeout: true,
  producesArtifacts: true,
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
