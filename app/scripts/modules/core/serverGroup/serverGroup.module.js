'use strict';

const angular = require('angular');

import {SERVER_GROUP_STATES} from './serverGroup.states';
import './ServerGroupSearchResultFormatter';

module.exports = angular
  .module('spinnaker.core.serverGroup', [
    require('./serverGroup.transformer.js'),
    require('./configure/common/serverGroup.configure.common.module.js'),
    require('./pod/runningTasksTag.directive.js'),
    require('./details/multipleServerGroups.controller.js'),
    require('./serverGroup.dataSource'),
    SERVER_GROUP_STATES,
  ]);
