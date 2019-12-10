'use strict';

const angular = require('angular');

import { TITUS_SERVER_GROUP_CONFIGURATION_SERVICE } from './serverGroupConfiguration.service';

export const TITUS_SERVERGROUP_CONFIGURE_SERVERGROUP_CONFIGURE_TITUS_MODULE = 'spinnaker.serverGroup.configure.titus';
export const name = TITUS_SERVERGROUP_CONFIGURE_SERVERGROUP_CONFIGURE_TITUS_MODULE; // for backwards compatibility
angular.module(TITUS_SERVERGROUP_CONFIGURE_SERVERGROUP_CONFIGURE_TITUS_MODULE, [
  TITUS_SERVER_GROUP_CONFIGURATION_SERVICE,
]);
