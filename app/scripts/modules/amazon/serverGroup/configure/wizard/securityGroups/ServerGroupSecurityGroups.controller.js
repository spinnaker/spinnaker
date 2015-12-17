'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.serverGroup.configure.aws.securityGroups.controller', [
    require('../../../../../core/modal/wizard/modalWizard.service.js'),
    require('./securityGroupsRemoved.directive.js'),
    require('./securityGroupSelector.directive.js'),
    require('./securityGroupsFooter.directive.js'),

  ])
  .controller('awsServerGroupSecurityGroupsCtrl', function($scope, modalWizardService) {

    modalWizardService.getWizard().markClean('security-groups');
    modalWizardService.getWizard().markComplete('security-groups');
  });
