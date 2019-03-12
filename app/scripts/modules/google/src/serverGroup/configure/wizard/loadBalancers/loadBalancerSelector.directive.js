'use strict';

const angular = require('angular');
import _ from 'lodash';

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
    function(gceHttpLoadBalancerUtils) {
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
