import { GroupExecutionLabel } from './GroupExecutionLabel';
import { GroupMarkerIcon } from './GroupMarkerIcon';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export const groupStage: IStageTypeConfig = {
  description: 'A group of stages',
  executionLabelComponent: GroupExecutionLabel,
  markerIcon: GroupMarkerIcon,
  key: 'group',
  label: 'Group',
  useCustomTooltip: true,
  synthetic: true,
  validators: [],
};

Registry.pipeline.registerStage(groupStage);
