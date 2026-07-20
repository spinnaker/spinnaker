import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';
import { Registry } from '../../../../registry';
import { CheckPreconditionsExecutionDetails } from './CheckPreconditionsExecutionDetails';
import { CheckPreconditionsStageConfig } from './CheckPreconditionsStageConfig';
import { checkPreconditionsStage } from './checkPreconditionsStage';

describe('checkPreconditionsStage', () => {
  beforeEach(() => {
    Registry.reinitialize();
    Registry.pipeline.registerStage(checkPreconditionsStage);
  });

  it('registers the Check Preconditions stage as a React stage config', () => {
    const stageConfig = Registry.pipeline.getStageConfig({ type: 'checkPreconditions' } as any);

    expect(stageConfig).toEqual(
      jasmine.objectContaining({
        label: 'Check Preconditions',
        description: 'Checks for preconditions before continuing',
        key: 'checkPreconditions',
        restartable: true,
        component: CheckPreconditionsStageConfig,
        executionDetailsSections: [CheckPreconditionsExecutionDetails, ExecutionDetailsTasks],
        strategy: true,
      }),
    );
  });

  it('does not register Angular-only config fields', () => {
    const stageConfig = Registry.pipeline.getStageConfig({ type: 'checkPreconditions' } as any) as any;

    expect(stageConfig.controller).toBeUndefined();
    expect(stageConfig.controllerAs).toBeUndefined();
    expect(stageConfig.templateUrl).toBeUndefined();
  });
});
