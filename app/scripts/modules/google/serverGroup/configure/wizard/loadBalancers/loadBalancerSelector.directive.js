'use strict';

import _ from 'lodash';
let angular = require('angular');

import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.loadBalancers.selector.directive', [
    INFRASTRUCTURE_CACHE_SERVICE,
    require('google/loadBalancer/elSevenUtils.service.js'),
    require('./elSevenOptions/elSevenOptionsGenerator.component.js'),
    require('../../serverGroupConfiguration.service.js'),
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
  }).controller('gceServerGroupLoadBalancerSelectorCtrl', function (elSevenUtils,
                                                                    gceServerGroupConfigurationService,
                                                                    infrastructureCaches) {
    this.getLoadBalancerRefreshTime = () => {
      return infrastructureCaches.get('loadBalancers').getStats().ageMax;
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

        return angular.isDefined(selected) && _.some(selected, s => {
            return index[s].loadBalancerType === 'HTTP' || index[s].loadBalancerType === 'SSL';
          });
      }
    };

    this.isElSeven = elSevenUtils.isElSeven;
  });
