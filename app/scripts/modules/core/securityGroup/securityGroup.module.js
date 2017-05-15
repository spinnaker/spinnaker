'use strict';

let angular = require('angular');

import {SECURITY_GROUP_STATES} from './securityGroup.states';
import {SECURITY_GROUP_FILTER} from './filter/securityGroup.filter.component';
import './SecurityGroupSearchResultFormatter';

module.exports = angular
  .module('spinnaker.core.securityGroup', [
    require('./AllSecurityGroupsCtrl.js'),
    SECURITY_GROUP_FILTER,
    require('./securityGroup.pod.directive.js'),
    require('./securityGroup.directive.js'),
    require('./securityGroup.dataSource'),
    SECURITY_GROUP_STATES,
  ]);
