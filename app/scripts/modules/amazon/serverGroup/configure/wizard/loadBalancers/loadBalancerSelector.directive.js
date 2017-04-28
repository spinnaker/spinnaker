'use strict';

let angular = require('angular');
import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';

module.exports = angular
  .module('spinnaker.amazon.serverGroup.configure.wizard.loadBalancers.selector.directive', [
    INFRASTRUCTURE_CACHE_SERVICE,
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
    let setLoadBalancerRefreshTime = () => {
      this.refreshTime = infrastructureCaches.get('loadBalancers').getStats().ageMax;
    };

    this.refreshLoadBalancers = () => {
      this.refreshing = true;
      awsServerGroupConfigurationService.refreshLoadBalancers(this.command).then(() => {
        this.refreshing = false;
        setLoadBalancerRefreshTime();
      });
    };

    setLoadBalancerRefreshTime();
  });
