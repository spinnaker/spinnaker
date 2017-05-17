import {IComponentOptions, module} from 'angular';

import './buttonBusyIndicator.component.less';

export class ButtonBusyIndicatorComponent implements IComponentOptions {
  public template = `<span us-spinner="{color: '#ffffff', left: '3px', top: '10px', radius:3, width: 2, length: 3}"></span>`;
}

export const BUTTON_BUSY_INDICATOR_COMPONENT = 'spinnaker.core.forms.buttonBusyIndicator.component';
module(BUTTON_BUSY_INDICATOR_COMPONENT, [])
  .component('buttonBusyIndicator', new ButtonBusyIndicatorComponent());
