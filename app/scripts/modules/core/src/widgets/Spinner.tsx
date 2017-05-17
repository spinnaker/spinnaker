import { module, IComponentOptions } from 'angular';

export interface ISpinnerProps {
  radius: number;
  width: number;
  length: number;
}

export class SpinnerWrapperComponent implements IComponentOptions {
  public template = '<span us-spinner="{radius: $ctrl.radius, width: $ctrl.width, length: $ctrl.length}"></span>';
  public bindings = { radius: '<', width: '<', length: '<' };
}

export const SPINNER_WRAPPER = 'spinnaker.core.widgets.spinnerWrapper.component';
module(SPINNER_WRAPPER, []).component('spinnerWrapper', new SpinnerWrapperComponent());
