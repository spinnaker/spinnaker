import '../../../../bootstrap/runtimeInitializers';
import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';
import { Registry } from '../../../../registry';
import { PipelineParametersExecutionDetails } from './PipelineParametersExecutionDetails';
import { PipelineStageConfig } from './PipelineStageConfig';
import { PipelineStageExecutionDetails } from './PipelineStageExecutionDetails';
import { pipelineStage } from './pipelineStage';

const registeredPipelineStage = Registry.pipeline.getStageConfig({ type: 'pipeline' } as any) as any;

describe('pipelineStage', () => {
  it('registers the Pipeline stage as a React stage config', () => {
    expect(registeredPipelineStage).toEqual(
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
  });
});
