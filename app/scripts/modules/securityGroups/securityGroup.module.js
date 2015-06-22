'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.securityGroup', [
    require('./AllSecurityGroupsCtrl.js'),
    require('./SecurityGroupCtrl.js'),
    require('./securityGroup.directive.js'),
    require('./securityGroup.read.service.js'),
    require('./securityGroup.write.service.js'),
    require('./securityGroupCounts.directive.js'),
    require('./details/aws/SecurityGroupDetailsCtrl.js'),
    require('./configure/aws/EditSecurityGroupCtrl.js'),
    require('./configure/aws/CreateSecurityGroupCtrl.js'),
    require('./SecurityGroupsNavCtrl.js')
  ]);
