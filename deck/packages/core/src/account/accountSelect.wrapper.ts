import { module } from 'angular';

import { AccountSelectInput } from './AccountSelectInput';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const ACCOUNT_SELECT_WRAPPER = 'spinnaker.core.account.select.wrapper';
const ngmodule = module(ACCOUNT_SELECT_WRAPPER, []);

ngmodule.component(
  'accountSelectWrapper',
  angularComponentFromReact(AccountSelectInput, 'accountSelectWrapper', [
    'accounts',
    'provider',
    'readOnly',
    'onChange',
    'value',
  ]),
);
