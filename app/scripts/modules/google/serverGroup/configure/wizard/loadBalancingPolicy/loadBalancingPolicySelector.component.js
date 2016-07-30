'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.loadBalancingPolicySelector.component', [
    require('../../../../../core/utils/lodash.js')
  ])
  .component('gceLoadBalancingPolicySelector', {
    bindings: {
      command: '='
    },
    templateUrl: require('./loadBalancingPolicySelector.component.html'),
    controller: function ($scope) {
      if (!this.command.loadBalancingPolicy) {
        this.command.loadBalancingPolicy = {
          balancingMode: 'UTILIZATION'
        };
      }

      this.setMaxUtilization = (view) => {
        this.command.loadBalancingPolicy.maxUtilization = view / 100;
      };

      this.setMaxUtilizationView = (maxUtilization) => {
        this.maxUtilizationView = safeDecimalToPercent(maxUtilization);
      };

      function safeDecimalToPercent (value) {
        if (value === 0) {
          return 0;
        }
        return value ? Math.round(value * 100) : undefined;
      }

      $scope.$on('$destroy', () => {
        delete this.command.loadBalancingPolicy;
      });

      $scope.$watch('$ctrl.command.loadBalancingPolicy.balancingMode', (mode) => {
        if (mode === 'RATE') {
          delete this.command.loadBalancingPolicy.maxUtilization;
        }
      });

      this.maxPort = 65535;
    }
  });
