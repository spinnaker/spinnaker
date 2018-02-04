import { module } from 'angular';

import { SERVER_GROUP_CONFIGURATION_SERVICE } from 'core/serverGroup/configure/common/serverGroupConfiguration.service';
import { SERVER_GROUP_STATES } from './serverGroup.states';
import { SERVER_GROUP_DATA_SOURCE } from './serverGroup.dataSource';
import './serverGroupSearchResultType';
import { VIEW_SCALING_ACTIVITIES_LINK } from './details/scalingActivities/viewScalingActivitiesLink.component';
import { DEPLOY_INITIALIZER_COMPONENT } from './configure/common/deployInitializer.component';

export const SERVERGROUP_MODULE = 'spinnaker.core.serverGroup';
module(SERVERGROUP_MODULE, [
  require('./serverGroup.transformer').name,
  SERVER_GROUP_CONFIGURATION_SERVICE,
  require('./configure/common/v2instanceArchetypeSelector.directive').name,
  require('./configure/common/v2InstanceTypeSelector.directive').name,
  require('./pod/runningTasksTag.directive').name,
  require('./details/multipleServerGroups.controller').name,
  SERVER_GROUP_DATA_SOURCE,
  require('./configure/common/basicSettingsMixin.controller').name,
  SERVER_GROUP_STATES,
  VIEW_SCALING_ACTIVITIES_LINK,
  DEPLOY_INITIALIZER_COMPONENT
]);
