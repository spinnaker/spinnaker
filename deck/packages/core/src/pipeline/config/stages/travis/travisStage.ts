import { TravisExecutionDetails } from './TravisExecutionDetails';
import { TravisExecutionLabel } from './TravisExecutionLabel';
import { TravisStageConfig } from './TravisStageConfig';
import { ExecutionDetailsTasks } from '../common';
import type { IStage } from '../../../../domain';
import { Registry } from '../../../../registry';

export const TRAVIS_STAGE = 'spinnaker.core.pipeline.stage.travisStage';

export const travisStage = {
  label: 'Travis',
  description: 'Runs a Travis job',
  key: 'travis',
  restartable: true,
  component: TravisStageConfig,
  producesArtifacts: true,
  executionDetailsSections: [TravisExecutionDetails, ExecutionDetailsTasks],
  executionLabelComponent: TravisExecutionLabel,
  providesVersionForBake: true,
  extraLabelLines: (stage: IStage) => {
    if (!stage.masterStage.context || !stage.masterStage.context.buildInfo) {
      return 0;
    }
    const lines = stage.masterStage.context.buildInfo.number ? 1 : 0;
    return lines + (stage.masterStage.context.buildInfo.testResults || []).length;
  },
  supportsCustomTimeout: true,
  validators: [{ type: 'requiredField', fieldName: 'job' }],
  strategy: true,
};

Registry.pipeline.registerStage(travisStage);
