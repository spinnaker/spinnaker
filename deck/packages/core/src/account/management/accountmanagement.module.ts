import { module } from 'angular';

import { ACCOUNT_MANAGEMENT_STATES } from './accountmanagement.states';

export const ACCOUNT_MANAGEMENT_MODULE = 'spinnaker.core.accountManagement';
module(ACCOUNT_MANAGEMENT_MODULE, [ACCOUNT_MANAGEMENT_STATES]);
