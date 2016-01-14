'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.artifactSettingsSelector', [
])
  .directive('cfServerGroupArtifactSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        application: '=',
        hideClusterNamePreview: '=',
      },
      templateUrl: require('./serverGroupArtifactSettingsDirective.html'),
      controller: 'cfServerGroupArtifactSettingsSelectorCtrl as artifactSettingsCtrl',
    };
  })
  .controller('cfServerGroupArtifactSettingsSelectorCtrl', function($scope) {

    if ($scope.command.repository === undefined) {
      $scope.command.repository = '';
    }

    if ($scope.command.artifact === undefined) {
      $scope.command.artifact = '';
    }

    this.getApplication = function() {
      var command = $scope.command;
      return command.application;
    };

    this.isS3 = function() {
      return $scope.command.repository.startsWith('s3://');
    };


    });
