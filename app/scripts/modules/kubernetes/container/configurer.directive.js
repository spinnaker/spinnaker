'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.container.configurer.directive', [])
  .directive('kubernetesContainerConfigurer', function () {
    return {
      restrict: 'E',
      templateUrl: require('./configurer.directive.html'),
      scope: {
        container: '=',
      }
    };
  })
  .controller('kubernetesContainerConfigurerController', function() {
    this.cpuPattern = /^\d+(m)?$/;
    this.memoryPattern = /^\d+(Mi|Gi)?$/;
  });
