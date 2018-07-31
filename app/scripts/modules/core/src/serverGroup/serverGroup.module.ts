import { module } from 'angular';

import { SERVER_GROUP_CONFIGURATION_SERVICE } from 'core/serverGroup/configure/common/serverGroupConfiguration.service';
import { SERVER_GROUP_STATES } from './serverGroup.states';
import { SERVER_GROUP_DATA_SOURCE } from './serverGroup.dataSource';
import './serverGroupSearchResultType';
import { VIEW_SCALING_ACTIVITIES_LINK } from './details/scalingActivities/viewScalingActivitiesLink.component';
import { DEPLOY_INITIALIZER_COMPONENT } from './configure/common/deployInitializer.component';
import { HEALTH_PERCENT_SELECTOR } from './configure/common/targetHealthyPercentageSelector.component';
import { V2_INSTANCE_ARCHETYPE_SELECTOR } from './configure/common/v2instanceArchetypeSelector.component';
import { V2_INSTANCE_TYPE_SELECTOR } from './configure/common/v2InstanceTypeSelector.component';

export const SERVERGROUP_MODULE = 'spinnaker.core.serverGroup';
module(SERVERGROUP_MODULE, [
  require('./serverGroup.transformer').name,
  SERVER_GROUP_CONFIGURATION_SERVICE,
  V2_INSTANCE_ARCHETYPE_SELECTOR,
  V2_INSTANCE_TYPE_SELECTOR,
  require('./pod/runningTasksTag.directive').name,
  require('./details/multipleServerGroups.controller').name,
  SERVER_GROUP_DATA_SOURCE,
  require('./configure/common/basicSettingsMixin.controller').name,
  SERVER_GROUP_STATES,
  VIEW_SCALING_ACTIVITIES_LINK,
  DEPLOY_INITIALIZER_COMPONENT,
  HEALTH_PERCENT_SELECTOR,
]);
