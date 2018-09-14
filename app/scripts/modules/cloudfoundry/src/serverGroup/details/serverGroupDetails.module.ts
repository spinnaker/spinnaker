import { module } from 'angular';

import { CLOUD_FOUNDRY_RESIZE_SERVER_GROUP_CTRL } from './resize/resizeServerGroup.controller';
import { CLOUD_FOUNDRY_ROLLBACK_SERVER_GROUP_CTRL } from './rollback/rollbackServerGroup.controller';

export const SERVER_GROUP_DETAILS_MODULE = 'spinnaker.cloudfoundry.serverGroup.details';
module(SERVER_GROUP_DETAILS_MODULE, [CLOUD_FOUNDRY_RESIZE_SERVER_GROUP_CTRL, CLOUD_FOUNDRY_ROLLBACK_SERVER_GROUP_CTRL]);
