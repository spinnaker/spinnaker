import { ManualJudgmentExecutionDetails } from './ManualJudgmentExecutionDetails';
import { ManualJudgmentExecutionLabel } from './ManualJudgmentExecutionLabel';
import { ManualJudgmentMarkerIcon } from './ManualJudgmentMarkerIcon';
import { ManualJudgmentStageConfig } from './ManualJudgmentStageConfig';
import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';
import { Registry } from '../../../../registry';

import './manualJudgmentExecutionDetails.less';

export const manualJudgmentStage = {
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
};

Registry.pipeline.registerStage(manualJudgmentStage);
