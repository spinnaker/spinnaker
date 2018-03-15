'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titus.advancedSettingSelector', [])
  .directive('titusServerGroupAdvancedSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./serverGroupAdvancedSettingsDirective.html'),
      controller: 'titusServerGroupAdvancedSettingsSelectorCtrl as advancedSettingsCtrl',
    };
  })
  .controller('titusServerGroupAdvancedSettingsSelectorCtrl', function($scope) {
    this.addInstanceMetadata = function() {
      $scope.command.instanceMetadata.push({});
    };

    this.removeInstanceMetadata = function(index) {
      $scope.command.instanceMetadata.splice(index, 1);
    };

    this.addTag = function() {
      $scope.command.tags.push({});
    };

    this.removeTag = function(index) {
      $scope.command.tags.splice(index, 1);
    };

  });
