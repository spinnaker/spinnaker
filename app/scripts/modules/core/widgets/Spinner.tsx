import * as React from 'react';
import { module, IComponentOptions } from 'angular';
import { angular2react } from 'angular2react';

import { ReactInjector } from 'core/react';

interface ISpinnerProps {
  radius: number;
  width: number;
  length: number;
}

class SpinnerWrapperComponent implements IComponentOptions {
  public template = '<span us-spinner="{radius: $ctrl.radius, width: $ctrl.width, length: $ctrl.length}"></span>';
  public bindings = { radius: '<', width: '<', length: '<' };
}

export const SPINNER_WRAPPER = 'spinnaker.core.widgets.spinnerWrapper.component';
module(SPINNER_WRAPPER, []).component('spinnerWrapper', new SpinnerWrapperComponent());

export let Spinner: React.ComponentClass<ISpinnerProps>;
ReactInjector.give(($injector: any) => Spinner = angular2react('spinnerWrapper', new SpinnerWrapperComponent(), $injector) as any);
