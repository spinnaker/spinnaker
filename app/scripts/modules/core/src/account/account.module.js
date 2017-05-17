'use strict';

import { ACCOUNT_TAG_COMPONENT } from './accountTag.component';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.account', [
  require('./providerToggles.directive'),
  require('./accountSelectField.directive'),
  require('./collapsibleAccountTag.directive'),
  ACCOUNT_TAG_COMPONENT,
]);
