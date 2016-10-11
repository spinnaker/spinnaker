'use strict';

let angular = require('angular');
import _ from 'lodash';

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.loadBalancers.selector.directive', [
    require('core/cache/infrastructureCaches.js'),
    require('../../serverGroupConfiguration.service.js'),
    require('./elSevenOptions/elSevenOptionsGenerator.component.js'),
  ])
  .directive('gceServerGroupLoadBalancerSelector', function () {
    return {
      restrict: 'E',
      templateUrl: require('./loadBalancerSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: 'gceServerGroupLoadBalancerSelectorCtrl',
    };
  }).controller('gceServerGroupLoadBalancerSelectorCtrl', function (gceServerGroupConfigurationService, infrastructureCaches) {
    this.getLoadBalancerRefreshTime = () => {
      return infrastructureCaches.loadBalancers.getStats().ageMax;
    };

    this.refreshLoadBalancers = () => {
      this.refreshing = true;
      gceServerGroupConfigurationService.refreshLoadBalancers(this.command).then(() => {
        this.refreshing = false;
      });
    };

    this.showLoadBalancingPolicy = () => {
      if (_.has(this, 'command.backingData.filtered.loadBalancerIndex')) {
        let index = this.command.backingData.filtered.loadBalancerIndex;
        let selected = this.command.loadBalancers;

        return angular.isDefined(selected) && _.some(selected, s => index[s].loadBalancerType === 'HTTP');
      }
    };
  });
