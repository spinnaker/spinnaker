import { module } from 'angular';

import { SERVER_GROUP_MANAGER_SERVICE } from './serverGroupManager.service';

export const SERVER_GROUP_MANAGER_MODULE = 'spinnaker.core.serverGroupManager.module';
module(SERVER_GROUP_MANAGER_MODULE, [
  SERVER_GROUP_MANAGER_SERVICE,
]);
