import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';
import { Registry } from '../../../../registry';
import { PipelineParametersExecutionDetails } from './PipelineParametersExecutionDetails';
import { PipelineStageConfig } from './PipelineStageConfig';
import { PipelineStageExecutionDetails } from './PipelineStageExecutionDetails';
import { pipelineStage } from './pipelineStage';

describe('pipelineStage', () => {
  beforeEach(() => {
    Registry.reinitialize();
    Registry.pipeline.registerStage(pipelineStage);
  });

  it('registers the Pipeline stage as a React stage config', () => {
    const stageConfig = Registry.pipeline.getStageConfig({ type: 'pipeline' } as any) as any;

    expect(stageConfig).toEqual(
      jasmine.objectContaining({
        label: 'Pipeline',
        description: 'Runs a pipeline',
        key: 'pipeline',
        restartable: true,
        component: PipelineStageConfig,
        executionDetailsSections: [
          PipelineStageExecutionDetails,
          PipelineParametersExecutionDetails,
          ExecutionDetailsTasks,
        ],
        supportsCustomTimeout: true,
        validators: [{ type: 'requiredField', fieldName: 'pipeline' }],
      }),
    );
    expect(stageConfig.templateUrl).toBeUndefined();
    expect(stageConfig.controller).toBeUndefined();
    expect(stageConfig.controllerAs).toBeUndefined();
  });
});
