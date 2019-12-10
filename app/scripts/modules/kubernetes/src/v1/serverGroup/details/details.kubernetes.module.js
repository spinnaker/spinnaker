'use strict';

import { KUBERNETES_LIFECYCLE_HOOK_DETAILS } from './lifecycleHookDetails.component';
import { KUBERNETES_SERVER_GROUP_CONTAINER_DETAIL } from './containerDetail.component';

const angular = require('angular');

export const KUBERNETES_V1_SERVERGROUP_DETAILS_DETAILS_KUBERNETES_MODULE = 'spinnaker.serverGroup.details.kubernetes';
export const name = KUBERNETES_V1_SERVERGROUP_DETAILS_DETAILS_KUBERNETES_MODULE; // for backwards compatibility
angular.module(KUBERNETES_V1_SERVERGROUP_DETAILS_DETAILS_KUBERNETES_MODULE, [
  require('./details.controller').name,
  require('./resize/resize.controller').name,
  require('./rollback/rollback.controller').name,
  KUBERNETES_LIFECYCLE_HOOK_DETAILS,
  KUBERNETES_SERVER_GROUP_CONTAINER_DETAIL,
]);
