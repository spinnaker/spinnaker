'use strict';

const angular = require('angular');

import { SERVER_GROUP_CONFIGURATION_SERVICE } from 'core/serverGroup/configure/common/serverGroupConfiguration.service';
import { SERVER_GROUP_STATES } from './serverGroup.states';
import './ServerGroupSearchResultFormatter';
import { VIEW_SCALING_ACTIVITIES_LINK } from './details/scalingActivities/viewScalingActivitiesLink.component';

module.exports = angular
  .module('spinnaker.core.serverGroup', [
    require('./serverGroup.transformer'),
    SERVER_GROUP_CONFIGURATION_SERVICE,
    require('./configure/common/v2instanceArchetypeSelector.directive'),
    require('./configure/common/v2InstanceTypeSelector.directive'),
    require('./pod/runningTasksTag.directive'),
    require('./details/multipleServerGroups.controller'),
    require('./serverGroup.dataSource'),
    require('./configure/common/basicSettingsMixin.controller'),
    SERVER_GROUP_STATES,
    VIEW_SCALING_ACTIVITIES_LINK
  ]);
