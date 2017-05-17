'use strict';

import {KUBERNETES_LIFECYCLE_HOOK_DETAILS} from './lifecycleHookDetails.component';

const angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.kubernetes', [
  require('./details.controller.js'),
  require('./resize/resize.controller.js'),
  require('./rollback/rollback.controller.js'),
  KUBERNETES_LIFECYCLE_HOOK_DETAILS,
]);
