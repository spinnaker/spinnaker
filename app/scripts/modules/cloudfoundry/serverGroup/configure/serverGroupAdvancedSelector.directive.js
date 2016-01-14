'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.advancedSelector', [
    ])
    .directive('cfServerGroupAdvancedSelector', function() {
      return {
        restrict: 'E',
        scope: {
          command: '=',
          application: '=',
          hideClusterNamePreview: '=',
        },
        templateUrl: require('./serverGroupAdvancedDirective.html'),
        controller: 'cfServerGroupAdvancedSelectorCtrl as advancedCtrl',
      };
    })
    .controller('cfServerGroupAdvancedSelectorCtrl', function($scope) {

      this.addService = function() {
        $scope.command.advanced.push('');
      };

      this.removeService = function(index) {
        $scope.command.advanced.splice(index, 1);
      };

      this.getApplication = function() {
        var command = $scope.command;
        return command.application;
      };

      $scope.memoryOptions = [
        {
          key: 2048,
          label: '2GB',
          description: ''
        },
        {
          key: 1024,
          label: '1GB',
          description: 'Recommended minimum'
        },
        {
          key: 512,
          label: '512MB',
          description: 'Absolute minimum for Spring Boot apps'
        },
      ];

    });
