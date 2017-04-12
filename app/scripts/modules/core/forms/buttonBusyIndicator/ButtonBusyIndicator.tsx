import * as React from 'react';
import { angular2react } from 'angular2react';

import { ButtonBusyIndicatorComponent } from './buttonBusyIndicator.component';

interface IButtonBusyIndicatorProps {
}

export let ButtonBusyIndicator: React.ComponentClass<IButtonBusyIndicatorProps> = undefined;
export const ButtonBusyIndicatorInject = ($injector: any) => {
  ButtonBusyIndicator = angular2react('buttonBusyIndicator', ButtonBusyIndicatorComponent, $injector) as React.ComponentClass<IButtonBusyIndicatorProps>;
};
