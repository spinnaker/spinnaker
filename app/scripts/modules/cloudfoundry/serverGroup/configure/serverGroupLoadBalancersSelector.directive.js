'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.loadBalancersSelector', [
    ])
    .directive('cfServerGroupLoadBalancersSelector', function() {
      return {
        restrict: 'E',
        scope: {
          command: '=',
          application: '=',
          hideClusterNamePreview: '=',
        },
        templateUrl: require('./serverGroupLoadBalancersDirective.html'),
        controller: 'cfServerGroupLoadBalancersSelectorCtrl as loadBalancersCtrl',
      };
    })
    .controller('cfServerGroupLoadBalancersSelectorCtrl', function($scope, $controller, rx, imageReader, namingService, $uibModalStack, $state) {

      this.getApplication = function() {
        var command = $scope.command;
        return command.application;
      };

    })
    .name;
