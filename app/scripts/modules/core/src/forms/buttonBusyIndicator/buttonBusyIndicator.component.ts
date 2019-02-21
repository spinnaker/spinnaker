import { IComponentOptions, module } from 'angular';

import './buttonBusyIndicator.component.less';

export const buttonBusyIndicatorComponent: IComponentOptions = {
  template: `<i us-spinner="{color: '#ffffff', left: '3px', top: '10px', radius:3, width: 2, length: 3}"></i>`
};

export const BUTTON_BUSY_INDICATOR_COMPONENT = 'spinnaker.core.forms.buttonBusyIndicator.component';
module(BUTTON_BUSY_INDICATOR_COMPONENT, []).component('buttonBusyIndicator', buttonBusyIndicatorComponent);
