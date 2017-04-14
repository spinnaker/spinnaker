import * as React from 'react';
import { angular2react } from 'angular2react';

import { AccountLabelColorComponent } from './accountLabelColor.component';

interface IAccountLabelColorProps {
  account: string;
}

export let AccountLabelColor: React.ComponentClass<IAccountLabelColorProps> = undefined;
export const AccountLabelColorInject = ($injector: any) => {
  AccountLabelColor = angular2react<IAccountLabelColorProps>('accountLabelColor', new AccountLabelColorComponent(), $injector);
};
