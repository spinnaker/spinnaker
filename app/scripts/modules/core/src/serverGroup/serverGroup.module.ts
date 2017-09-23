import { module } from 'angular';

import { SERVER_GROUP_CONFIGURATION_SERVICE } from 'core/serverGroup/configure/common/serverGroupConfiguration.service';
import { SERVER_GROUP_STATES } from './serverGroup.states';
import './ServerGroupSearchResultFormatter';
import { VIEW_SCALING_ACTIVITIES_LINK } from './details/scalingActivities/viewScalingActivitiesLink.component';
import { DEPLOY_INITIALIZER_COMPONENT } from './configure/common/deployInitializer.component';
import { SearchResultHydratorRegistry } from 'core/search/searchResult/SearchResultHydratorRegistry';
import { ServerGroupSearchResultHydrator } from './ServerGroupSearchResultHydrator';
import { ApplicationReader } from 'core/application/service/application.read.service';

export const SERVERGROUP_MODULE = 'spinnaker.core.serverGroup';
module(SERVERGROUP_MODULE, [
  require('./serverGroup.transformer'),
  SERVER_GROUP_CONFIGURATION_SERVICE,
  require('./configure/common/v2instanceArchetypeSelector.directive'),
  require('./configure/common/v2InstanceTypeSelector.directive'),
  require('./pod/runningTasksTag.directive'),
  require('./details/multipleServerGroups.controller'),
  require('./serverGroup.dataSource'),
  require('./configure/common/basicSettingsMixin.controller'),
  SERVER_GROUP_STATES,
  VIEW_SCALING_ACTIVITIES_LINK,
  DEPLOY_INITIALIZER_COMPONENT
])
  .run((applicationReader: ApplicationReader) => SearchResultHydratorRegistry.register('serverGroups', new ServerGroupSearchResultHydrator(applicationReader)));
