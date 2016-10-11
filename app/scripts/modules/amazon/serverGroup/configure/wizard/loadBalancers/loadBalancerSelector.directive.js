'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.configure.wizard.loadBalancers.selector.directive', [
    require('core/cache/infrastructureCaches.js'),
    require('../../serverGroupConfiguration.service.js'),
  ])
  .directive('awsServerGroupLoadBalancerSelector', function () {
    return {
      restrict: 'E',
      templateUrl: require('./loadBalancerSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: 'awsServerGroupLoadBalancerSelectorCtrl',
    };
  }).controller('awsServerGroupLoadBalancerSelectorCtrl', function (awsServerGroupConfigurationService, infrastructureCaches) {
    this.getLoadBalancerRefreshTime = () => {
      return infrastructureCaches.loadBalancers.getStats().ageMax;
    };

    this.refreshLoadBalancers = () => {
      this.refreshing = true;
      awsServerGroupConfigurationService.refreshLoadBalancers(this.command).then(() => {
        this.refreshing = false;
      });
    };
  });
