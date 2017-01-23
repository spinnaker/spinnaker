'use strict';

let angular = require('angular');

import {SECURITY_GROUP_STATES} from './securityGroup.states';

module.exports = angular
  .module('spinnaker.core.securityGroup', [
    require('./AllSecurityGroupsCtrl.js'),
    require('./filter/SecurityGroupFilterCtrl.js'),
    require('./securityGroup.pod.directive.js'),
    require('./securityGroup.directive.js'),
    require('./securityGroup.dataSource'),
    SECURITY_GROUP_STATES,
  ]);
