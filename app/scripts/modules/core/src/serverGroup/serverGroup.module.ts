import { module } from 'angular';

import { SERVER_GROUP_CONFIGURATION_SERVICE } from './configure/common/serverGroupConfiguration.service';
import { SERVER_GROUP_STATES } from './serverGroup.states';
import { SERVER_GROUP_DATA_SOURCE } from './serverGroup.dataSource';
import './serverGroupSearchResultType';
import { VIEW_SCALING_ACTIVITIES_LINK } from './details/scalingActivities/viewScalingActivitiesLink.component';
import { DEPLOY_INITIALIZER_COMPONENT } from './configure/common/deployInitializer.component';
import { HEALTH_PERCENT_SELECTOR } from './configure/common/targetHealthyPercentageSelector.component';
import { V2_INSTANCE_ARCHETYPE_SELECTOR } from './configure/common/v2instanceArchetypeSelector.component';
import { V2_INSTANCE_TYPE_SELECTOR } from './configure/common/v2InstanceTypeSelector.component';
import { CORE_SERVERGROUP_SERVERGROUP_TRANSFORMER } from './serverGroup.transformer';
import { CORE_SERVERGROUP_POD_RUNNINGTASKSTAG_DIRECTIVE } from './pod/runningTasksTag.directive';
import { CORE_SERVERGROUP_DETAILS_MULTIPLESERVERGROUPS_CONTROLLER } from './details/multipleServerGroups.controller';
import { CORE_SERVERGROUP_CONFIGURE_COMMON_BASICSETTINGSMIXIN_CONTROLLER } from './configure/common/basicSettingsMixin.controller';

export const SERVERGROUP_MODULE = 'spinnaker.core.serverGroup';
module(SERVERGROUP_MODULE, [
  CORE_SERVERGROUP_SERVERGROUP_TRANSFORMER,
  SERVER_GROUP_CONFIGURATION_SERVICE,
  V2_INSTANCE_ARCHETYPE_SELECTOR,
  V2_INSTANCE_TYPE_SELECTOR,
  CORE_SERVERGROUP_POD_RUNNINGTASKSTAG_DIRECTIVE,
  CORE_SERVERGROUP_DETAILS_MULTIPLESERVERGROUPS_CONTROLLER,
  SERVER_GROUP_DATA_SOURCE,
  CORE_SERVERGROUP_CONFIGURE_COMMON_BASICSETTINGSMIXIN_CONTROLLER,
  SERVER_GROUP_STATES,
  VIEW_SCALING_ACTIVITIES_LINK,
  DEPLOY_INITIALIZER_COMPONENT,
  HEALTH_PERCENT_SELECTOR,
]);
