'use strict';

const angular = require('angular');

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
    .controller('cfServerGroupLoadBalancersSelectorCtrl', function($scope) {

      this.getApplication = function() {
        var command = $scope.command;
        return command.application;
      };

    });
