import * as React from 'react';
import { angular2react } from 'angular2react';

import { AccountTagComponent } from './accountTag.component';
import { ReactInjector } from 'core/react';

interface IAccountTagProps {
  account: string;
}

export let AccountTag: React.ComponentClass<IAccountTagProps> = undefined;
ReactInjector.give(($injector: any) => AccountTag = angular2react('accountTag', new AccountTagComponent(), $injector) as any);
