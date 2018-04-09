'use strict';

import { KUBERNETES_LIFECYCLE_HOOK_DETAILS } from './lifecycleHookDetails.component';
import { KUBERNETES_SERVER_GROUP_CONTAINER_DETAIL } from './containerDetail.component';

const angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.kubernetes', [
  require('./details.controller.js').name,
  require('./resize/resize.controller.js').name,
  require('./rollback/rollback.controller.js').name,
  KUBERNETES_LIFECYCLE_HOOK_DETAILS,
  KUBERNETES_SERVER_GROUP_CONTAINER_DETAIL,
]);
