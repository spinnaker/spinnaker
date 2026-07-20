import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';
import { Registry } from '../../../../registry';
import { ManualJudgmentExecutionDetails } from './ManualJudgmentExecutionDetails';
import { ManualJudgmentExecutionLabel } from './ManualJudgmentExecutionLabel';
import { ManualJudgmentMarkerIcon } from './ManualJudgmentMarkerIcon';
import { ManualJudgmentStageConfig } from './ManualJudgmentStageConfig';
import { manualJudgmentStage } from './manualJudgmentStage';

describe('manualJudgmentStage', () => {
  beforeEach(() => {
    Registry.reinitialize();
    Registry.pipeline.registerStage(manualJudgmentStage);
  });

  it('registers the Manual Judgment stage as a React stage config', () => {
    const stageConfig = Registry.pipeline.getStageConfig({ type: 'manualJudgment' } as any);

    expect(stageConfig).toEqual(
      jasmine.objectContaining({
        label: 'Manual Judgment',
        description: 'Waits for user approval before continuing',
        key: 'manualJudgment',
        restartable: true,
        component: ManualJudgmentStageConfig,
        executionDetailsSections: [ManualJudgmentExecutionDetails, ExecutionDetailsTasks],
        executionLabelComponent: ManualJudgmentExecutionLabel,
        useCustomTooltip: true,
        markerIcon: ManualJudgmentMarkerIcon,
        strategy: true,
        supportsCustomTimeout: true,
        disableNotifications: true,
      }),
    );
  });

  it('does not register Angular-only config fields', () => {
    const stageConfig = Registry.pipeline.getStageConfig({ type: 'manualJudgment' } as any) as any;

    expect(stageConfig.controller).toBeUndefined();
    expect(stageConfig.controllerAs).toBeUndefined();
    expect(stageConfig.templateUrl).toBeUndefined();
  });
});
