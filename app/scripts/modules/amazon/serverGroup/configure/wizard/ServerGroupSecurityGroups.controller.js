'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.serverGroup.configure.aws.securityGroups.controller', [
    require('../../../../core/modal/wizard/modalWizard.service.js'),
    require('../../../../core/cache/infrastructureCaches.js'),
    require('../serverGroupConfiguration.service.js'),
  ])
  .controller('awsServerGroupSecurityGroupsCtrl', function($scope, modalWizardService, infrastructureCaches,
                                                           awsServerGroupConfigurationService) {

    $scope.getSecurityGroupRefreshTime = function() {
      return infrastructureCaches.securityGroups.getStats().ageMax;
    };

    $scope.refreshSecurityGroups = function() {
      $scope.refreshing = true;
      awsServerGroupConfigurationService.refreshSecurityGroups($scope.command).then(function() {
        $scope.refreshing = false;
      });
    };

    modalWizardService.getWizard().markClean('security-groups');
    modalWizardService.getWizard().markComplete('security-groups');
  }).name;
