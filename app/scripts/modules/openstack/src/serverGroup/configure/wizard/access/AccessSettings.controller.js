'use strict';

import { FirewallLabels, ModalWizard } from '@spinnaker/core';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.serverGroup.configure.openstack.accessSettings', [
    require('@uirouter/angularjs').default,
    require('angular-ui-bootstrap'),
    require('../../../../common/cacheBackedMultiSelectField.directive').name,
  ])
  .controller('openstackServerGroupAccessSettingsCtrl', [
    '$scope',
    function($scope) {
      $scope.firewallsLabel = FirewallLabels.get('Firewalls');

      // Loads all load balancers in the current application, region, and account
      $scope.updateLoadBalancers = function() {
        var filter = {
          account: $scope.command.credentials,
          region: $scope.command.region,
        };
        $scope.application.loadBalancers.refresh();
        $scope.allLoadBalancers = _.filter($scope.application.loadBalancers.data, filter);
      };
      $scope.$watch('command.credentials', $scope.updateLoadBalancers);
      $scope.$watch('command.region', $scope.updateLoadBalancers);
      $scope.$watch('application.loadBalancers', $scope.updateLoadBalancers);
      $scope.updateLoadBalancers();

      $scope.$watch('command.associatePublicIpAddress', resetFloatingNetworkIp);

      // Loads all firewalls in the current region and account
      $scope.updateSecurityGroups = function() {
        $scope.allSecurityGroups = getSecurityGroups();
      };
      // The backingData the getSecurityGroups gets resolved after the form is loaded, so lets watch it
      $scope.$watch(function() {
        return _.map(getSecurityGroups(), 'id').join();
      }, $scope.updateSecurityGroups);

      $scope.$watch('accessSettings.$valid', function(newVal) {
        if (newVal) {
          ModalWizard.markClean('access-settings');
          ModalWizard.markComplete('access-settings');
        } else {
          ModalWizard.markIncomplete('access-settings');
        }
      });

      function getSecurityGroups() {
        var account = $scope.command.credentials;
        var region = $scope.command.region;
        if (!account || !region) {
          return [];
        } else {
          return _.get($scope.command.backingData.securityGroups, [account, 'openstack', region]);
        }
      }

      function resetFloatingNetworkIp() {
        if ($scope.command.associatePublicIpAddress === false) {
          $scope.command.floatingNetworkId = undefined;
        }
      }
    },
  ]);
