'use strict';

import {KUBERNETES_LIFECYCLE_HOOK_DETAILS} from './lifecycleHookDetails.component';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.kubernetes', [
  require('core/account/account.module.js'),
  require('./details.controller.js'),
  require('./resize/resize.controller.js'),
  require('./rollback/rollback.controller.js'),
  KUBERNETES_LIFECYCLE_HOOK_DETAILS,
]);
