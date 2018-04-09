import { IController, module } from 'angular';

import { GroupExecutionLabel } from './GroupExecutionLabel';
import { GroupMarkerIcon } from './GroupMarkerIcon';
import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

export const GROUP_STAGE = 'spinnaker.core.pipeline.stage.groupStage';

export class GroupStage implements IController {}

module(GROUP_STAGE, [PIPELINE_CONFIG_PROVIDER])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerStage({
      controller: 'GroupStageCtrl',
      description: 'A group of stages',
      executionLabelComponent: GroupExecutionLabel,
      markerIcon: GroupMarkerIcon,
      key: 'group',
      label: 'Group',
      templateUrl: require('./groupStage.html'),
      useCustomTooltip: true,
      synthetic: true,
      validators: [],
    });
  })
  .controller('GroupStageCtrl', GroupStage);
