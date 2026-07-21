import type { IComponentOptions } from 'angular';
import { module } from 'angular';
import React from 'react';

import { AngularJSAdapter } from '../../reactShims';

import type { IStageConstant } from './stages/stageConstants';

export interface ITargetSelectProps {
  options: IStageConstant[];
  model: { target: string };
  onChange(target: string): void;
}

export function TargetSelect(props: ITargetSelectProps) {
  return React.createElement(AngularJSAdapter, {
    template: `
        <target-select
          options="props.options"
          model="props.model"
          on-change="props.onChange">
        </target-select>
      `,
    locals: props,
  });
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
