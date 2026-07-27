import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';
import { Registry } from '../../../../registry';
import { EvaluateArtifactsExecutionDetails } from './EvaluateArtifactsExecutionDetails';
import { EvaluateArtifactsStageConfig } from './EvaluateArtifactsStageConfig';
import { evaluateArtifactsStage } from './evaluateArtifactsStage';

describe('evaluateArtifactsStage', () => {
  beforeEach(() => {
    Registry.reinitialize();
    Registry.pipeline.registerStage(evaluateArtifactsStage);
  });

  it('registers the Evaluate Artifacts stage as a React stage config', () => {
    const stageConfig = Registry.pipeline.getStageConfig({ type: 'evaluateArtifacts' } as any);

    expect(stageConfig).toEqual(
      jasmine.objectContaining({
        label: 'Evaluate Artifacts',
        description: 'Evaluates SpEL expressions in artifact contents and produces embedded artifacts.',
        key: 'evaluateArtifacts',
        component: EvaluateArtifactsStageConfig,
        executionDetailsSections: [EvaluateArtifactsExecutionDetails, ExecutionDetailsTasks],
        producesArtifacts: true,
      }),
    );
  });

  it('does not register Angular-only config fields', () => {
    const stageConfig = Registry.pipeline.getStageConfig({ type: 'evaluateArtifacts' } as any) as any;

    expect(stageConfig.controller).toBeUndefined();
    expect(stageConfig.controllerAs).toBeUndefined();
    expect(stageConfig.templateUrl).toBeUndefined();
  });
});
