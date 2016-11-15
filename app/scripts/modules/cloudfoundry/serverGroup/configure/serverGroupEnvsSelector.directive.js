'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.envsSelector', [
    ])
    .directive('cfServerGroupEnvsSelector', function() {
      return {
        restrict: 'E',
        scope: {
          command: '=',
          application: '=',
          hideClusterNamePreview: '=',
        },
        templateUrl: require('./serverGroupEnvsDirective.html'),
        controller: 'cfServerGroupEnvsSelectorCtrl as envsCtrl',
      };
    })
    .controller('cfServerGroupEnvsSelectorCtrl', function($scope) {

      this.addEnv = function() {
        if ($scope.command.envs === undefined) {
          $scope.command.envs = [];
        }
        $scope.command.envs.push({});
      };

      this.removeEnv = function(index) {
        $scope.command.envs.splice(index, 1);
      };

      this.getApplication = function() {
        var command = $scope.command;
        return command.application;
      };

    });
