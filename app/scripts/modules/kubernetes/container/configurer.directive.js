'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.container.configurer.directive', [])
  .directive('kubernetesContainerConfigurer', function () {
    return {
      restrict: 'E',
      templateUrl: require('./configurer.directive.html'),
      scope: {
        container: '=',
        index: '=',
        command: '=',
      }
    };
  })
  .controller('kubernetesContainerConfigurerController', function($scope) {
    this.cpuPattern = /^\d+(m)?$/;
    this.memoryPattern = /^\d+(Mi|Gi)?$/;

    this.removePort = function(index) {
      $scope.container.ports.splice(index, 1);
    };

    this.addPort = function() {
      $scope.container.ports.push({ protocol: 'TCP' });
    };

    this.protocols = ['TCP', 'UDP'];
    this.maxPort = 65535;
  });
