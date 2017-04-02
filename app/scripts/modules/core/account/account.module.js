'use strict';

import {ACCOUNT_LABEL_COLOR_COMPONENT} from './accountLabelColor.component';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.account', [
  require('./accountTag.directive.js'),
  require('./providerToggles.directive.js'),
  ACCOUNT_LABEL_COLOR_COMPONENT,
]);
