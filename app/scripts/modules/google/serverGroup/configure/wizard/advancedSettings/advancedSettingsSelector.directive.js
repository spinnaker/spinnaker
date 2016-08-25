'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.advancedSettings.selector.directive', [
    require('exports?"ui.select"!ui-select'),
    require('../../../../../core/cache/infrastructureCaches.js'),
    require('../../serverGroupConfiguration.service.js'),
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
  .controller('gceServerGroupAdvancedSettingsSelectorCtrl', function(gceServerGroupConfigurationService, infrastructureCaches) {
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

    this.setAutoHealing = () => {
      if (this.command.enableAutoHealing) {
        this.command.autoHealingPolicy = {initialDelaySec: 300};
      } else {
        this.command.autoHealingPolicy = {};
      }
    };

    this.getHttpHealthCheckRefreshTime = () => {
      return infrastructureCaches.httpHealthChecks.getStats().ageMax;
    };

    this.refreshHttpHealthChecks = () => {
      this.refreshing = true;
      gceServerGroupConfigurationService.refreshHttpHealthChecks(this.command).then(() => {
        this.refreshing = false;
      });
    };
  });
