import { EvaluateArtifactsExecutionDetails } from './EvaluateArtifactsExecutionDetails';
import { EvaluateArtifactsStageConfig } from './EvaluateArtifactsStageConfig';
import { ExecutionDetailsTasks } from '../common';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const EVALUATE_ARTIFACTS_STAGE = 'spinnaker.core.pipeline.stage.evaluateArtifacts';

export const evaluateArtifactsStage: IStageTypeConfig = {
  label: 'Evaluate Artifacts',
  description: 'Evaluates SpEL expressions in artifact contents and produces embedded artifacts.',
  key: 'evaluateArtifacts',
  component: EvaluateArtifactsStageConfig,
  executionDetailsSections: [EvaluateArtifactsExecutionDetails, ExecutionDetailsTasks],
  producesArtifacts: true,
};

Registry.pipeline.registerStage(evaluateArtifactsStage);
