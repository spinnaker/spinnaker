import { module, IComponentOptions } from 'angular';
import { IStageConstant } from 'core';

export interface ITargetSelectProps {
  options: IStageConstant[];
  model: { target: string };
  onChange(target: string): void;
}

export class TargetSelectComponent implements IComponentOptions {
  public bindings = {
    options: '=',
    model: '=',
    onChange: '<?',
  };
  public templateUrl = require('./targetSelect.html');
}

export const TARGET_SELECT_COMPONENT = 'spinnaker.pipeline.targetSelect.component';
module(TARGET_SELECT_COMPONENT, []).component('targetSelect', new TargetSelectComponent());
