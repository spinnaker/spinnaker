import { IComponentOptions, module } from 'angular';

import { IStageConstant } from './stages/stageConstants';

export interface ITargetSelectProps {
  options: IStageConstant[];
  model: { target: string };
  onChange(target: string): void;
}

export const targetSelectComponent: IComponentOptions = {
  bindings: {
    options: '=',
    model: '=',
    onChange: '<?',
  },
  templateUrl: require('./targetSelect.html'),
};

export const TARGET_SELECT_COMPONENT = 'spinnaker.pipeline.targetSelect.component';
module(TARGET_SELECT_COMPONENT, []).component('targetSelect', targetSelectComponent);
