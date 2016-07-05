'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.advancedSettings.selector.directive', [
    require('exports?"ui.select"!ui-select'),
  ])
  .directive('gceServerGroupAdvancedSettingsSelector', function() {
    return {
      restrict: 'E',
      templateUrl: require('./advancedSettings.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: 'gceServerGroupAdvancedSettingsSelectorCtrl',
    };
  })
  .controller('gceServerGroupAdvancedSettingsSelectorCtrl', function() {
    this.addTag = () => {
      this.command.tags.push({});
    };

    this.removeTag = (index) => {
      this.command.tags.splice(index, 1);
    };

    this.setPreemptible = () => {
      if (this.command.preemptible) {
        this.command.automaticRestart = false;
        this.command.onHostMaintenance = 'TERMINATE';
      } else {
        this.command.automaticRestart = true;
        this.command.onHostMaintenance = 'MIGRATE';
      }
    };
  });
