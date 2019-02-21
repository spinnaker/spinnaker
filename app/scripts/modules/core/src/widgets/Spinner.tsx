import { module, IComponentOptions } from 'angular';

export interface ILegacySpinnerProps {
  radius: number;
  width: number;
  length: number;
}

export const spinnerWrapperComponent: IComponentOptions = {
  template: '<span us-spinner="{radius: $ctrl.radius, width: $ctrl.width, length: $ctrl.length}"></span>',
  bindings: { radius: '<', width: '<', length: '<' },
};

export const SPINNER_WRAPPER = 'spinnaker.core.widgets.spinnerWrapper.component';
module(SPINNER_WRAPPER, []).component('spinnerWrapper', spinnerWrapperComponent);
