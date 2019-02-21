'use strict';

const angular = require('angular');
import _ from 'lodash';

import { InfrastructureCaches } from '@spinnaker/core';
import { GCE_HTTP_LOAD_BALANCER_UTILS } from 'google/loadBalancer/httpLoadBalancerUtils.service';

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.loadBalancers.selector.directive', [
    GCE_HTTP_LOAD_BALANCER_UTILS,
    require('./elSevenOptions/elSevenOptionsGenerator.component').name,
    require('../../serverGroupConfiguration.service').name,
  ])
  .directive('gceServerGroupLoadBalancerSelector', function() {
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
  })
  .controller('gceServerGroupLoadBalancerSelectorCtrl', [
    'gceHttpLoadBalancerUtils',
    'gceServerGroupConfigurationService',
    function(gceHttpLoadBalancerUtils, gceServerGroupConfigurationService) {
      this.getLoadBalancerRefreshTime = () => {
        return InfrastructureCaches.get('loadBalancers').getStats().ageMax;
      };

      this.refreshLoadBalancers = () => {
        this.refreshing = true;
        gceServerGroupConfigurationService.refreshLoadBalancers(this.command).then(() => {
          this.refreshing = false;
        });
      };

      this.showLoadBalancingPolicy = () => {
        if (_.has(this, 'command.backingData.filtered.loadBalancerIndex')) {
          const index = this.command.backingData.filtered.loadBalancerIndex;
          const selected = this.command.loadBalancers;

          return (
            angular.isDefined(selected) &&
            _.some(selected, s => {
              return (
                index[s].loadBalancerType === 'HTTP' ||
                index[s].loadBalancerType === 'SSL' ||
                index[s].loadBalancerType === 'TCP'
              );
            })
          );
        }
      };

      this.isHttpLoadBalancer = lb => gceHttpLoadBalancerUtils.isHttpLoadBalancer(lb);
    },
  ]);
