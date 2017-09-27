'use strict';

import { ACCOUNT_TAG_COMPONENT } from './accountTag.component';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.account', [
  require('./providerToggles.directive').name,
  require('./accountSelectField.directive').name,
  require('./collapsibleAccountTag.directive').name,
  ACCOUNT_TAG_COMPONENT,
]);
