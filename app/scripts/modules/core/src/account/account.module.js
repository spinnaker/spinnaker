'use strict';

import { ACCOUNT_SELECT_FIELD_WRAPPER } from './accountSelectFieldWrapper.component';
import { ACCOUNT_TAG_COMPONENT } from './accountTag.component';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.account', [
  require('./providerToggles.directive').name,
  require('./accountSelectField.directive').name,
  require('./collapsibleAccountTag.directive').name,
  ACCOUNT_SELECT_FIELD_WRAPPER,
  ACCOUNT_TAG_COMPONENT,
]);
