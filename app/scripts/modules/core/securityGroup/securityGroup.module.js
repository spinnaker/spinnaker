'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.securityGroup', [
    require('./AllSecurityGroupsCtrl.js'),
    require('./filter/SecurityGroupFilterCtrl.js'),
    require('./securityGroup.pod.directive.js'),
    require('./securityGroup.directive.js'),
    require('./securityGroup.read.service.js'),
    require('./securityGroup.write.service.js'),
  ]);
