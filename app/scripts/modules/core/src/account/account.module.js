'use strict';

import { ACCOUNT_TAG_COMPONENT } from './accountTag.component';
import { ACCOUNT_SELECT_WRAPPER } from './accountSelect.wrapper';
import { ACCOUNT_SELECT_COMPONENT } from './accountSelectField.component';

const angular = require('angular');

export const CORE_ACCOUNT_ACCOUNT_MODULE = 'spinnaker.core.account';
export const name = CORE_ACCOUNT_ACCOUNT_MODULE; // for backwards compatibility
angular.module(CORE_ACCOUNT_ACCOUNT_MODULE, [
  require('./providerToggles.directive').name,
  ACCOUNT_SELECT_COMPONENT,
  require('./collapsibleAccountTag.directive').name,
  ACCOUNT_TAG_COMPONENT,
  ACCOUNT_SELECT_WRAPPER,
]);
