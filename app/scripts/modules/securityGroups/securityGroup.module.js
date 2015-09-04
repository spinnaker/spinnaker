'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.securityGroup', [
    require('./AllSecurityGroupsCtrl.js'),
    require('./filter/SecurityGroupFilterCtrl.js'),
    require('./securityGroup.pod.directive.js'),
    require('./securityGroup.directive.js'),
    require('./securityGroup.read.service.js'),
    require('./securityGroup.write.service.js'),
    require('./securityGroupCounts.directive.js'),
    require('./SecurityGroupsNavCtrl.js')
  ]).name;
