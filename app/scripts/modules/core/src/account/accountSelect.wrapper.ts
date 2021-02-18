import { module } from 'angular';
import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { react2angular } from 'react2angular';

import { AccountSelectInput } from './AccountSelectInput';

export const ACCOUNT_SELECT_WRAPPER = 'spinnaker.core.account.select.wrapper';
const ngmodule = module(ACCOUNT_SELECT_WRAPPER, []);

ngmodule.component(
  'accountSelectWrapper',
  react2angular(withErrorBoundary(AccountSelectInput, 'accountSelectWrapper'), [
    'accounts',
    'provider',
    'readOnly',
    'onChange',
    'value',
  ]),
);
