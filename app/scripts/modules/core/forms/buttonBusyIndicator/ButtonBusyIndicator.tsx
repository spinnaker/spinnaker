import * as React from 'react';
import { angular2react } from 'angular2react';

import { ButtonBusyIndicatorComponent } from './buttonBusyIndicator.component';
import { ReactInjector } from 'core/react.module';

interface IButtonBusyIndicatorProps {
}

export let ButtonBusyIndicator: React.ComponentClass<IButtonBusyIndicatorProps> = undefined;
ReactInjector.give(($injector: any) => ButtonBusyIndicator = angular2react('buttonBusyIndicator', new ButtonBusyIndicatorComponent(), $injector) as any);
