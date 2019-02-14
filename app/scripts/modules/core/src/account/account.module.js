'use strict';

import { ACCOUNT_TAG_COMPONENT } from './accountTag.component';
import { ACCOUNT_SELECT_WRAPPER } from './accountSelect.wrapper';
import { ACCOUNT_SELECT_COMPONENT } from './accountSelectField.component';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.account', [
  require('./providerToggles.directive').name,
  ACCOUNT_SELECT_COMPONENT,
  require('./collapsibleAccountTag.directive').name,
  ACCOUNT_TAG_COMPONENT,
  ACCOUNT_SELECT_WRAPPER,
]);
