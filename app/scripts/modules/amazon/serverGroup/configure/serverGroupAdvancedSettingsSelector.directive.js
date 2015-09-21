'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.aws.advancedSettings', [])
  .directive('awsServerGroupAdvancedSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./serverGroupAdvancedSettingsDirective.html'),
      controller: 'awsServerGroupAdvancedSettingsSelectorCtrl as advancedSettingsCtrl',
    };
  })
  .controller('awsServerGroupAdvancedSettingsSelectorCtrl', function() {
  }).name;
