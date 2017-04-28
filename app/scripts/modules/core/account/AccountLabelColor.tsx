import * as React from 'react';
import { angular2react } from 'angular2react';

import { AccountLabelColorComponent } from './accountLabelColor.component';
import { ReactInjector } from 'core/react.module';

interface IAccountLabelColorProps {
  account: string;
}

export let AccountLabelColor: React.ComponentClass<IAccountLabelColorProps> = undefined;
ReactInjector.give(($injector: any) => AccountLabelColor = angular2react('accountLabelColor', new AccountLabelColorComponent(), $injector) as any);
