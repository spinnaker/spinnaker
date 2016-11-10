'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.openstack.accessSettings', [
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
  require('../../../../common/cacheBackedMultiSelectField.directive.js'),
]).controller('openstackServerGroupAccessSettingsCtrl', function($scope, loadBalancerReader, securityGroupReader, networkReader, v2modalWizardService) {

  // Loads all load balancers in the current application, region, and account
  $scope.updateLoadBalancers = function() {
    var filter = {
      account: $scope.command.credentials,
      region: $scope.command.region
    };
    $scope.application.loadBalancers.refresh();
    $scope.allLoadBalancers = _.filter($scope.application.loadBalancers.data, filter);
  };
  $scope.$watch('command.credentials', $scope.updateLoadBalancers);
  $scope.$watch('command.region', $scope.updateLoadBalancers);
  $scope.$watch('application.loadBalancers', $scope.updateLoadBalancers);
  $scope.updateLoadBalancers();

  // Loads all security groups in the current region and account
  $scope.updateSecurityGroups = function() {
    $scope.allSecurityGroups = getSecurityGroups();
  };
  // The backingData the getSecurityGroups gets resolved after the form is loaded, so lets watch it
  $scope.$watch(function() {
    return _.map(getSecurityGroups(), 'id').join();
  }, $scope.updateSecurityGroups);

  $scope.$watch('accessSettings.$valid', function(newVal) {
    if (newVal) {
      v2modalWizardService.markClean('access-settings');
      v2modalWizardService.markComplete('access-settings');
    } else {
      v2modalWizardService.markIncomplete('access-settings');
    }
  });

  function getSecurityGroups() {
    return _.get($scope.command.backingData, 'filtered.securityGroups', []);
  }

});
