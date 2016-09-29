'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.advancedSettings.selector.directive', [
    require('exports?"ui.select"!ui-select'),
    require('../../../../../core/cache/infrastructureCaches.js'),
    require('../../serverGroupConfiguration.service.js'),
    require('../securityGroups/tagManager.service.js')
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
  .controller('gceServerGroupAdvancedSettingsSelectorCtrl', function(gceServerGroupConfigurationService,
                                                                     infrastructureCaches,
                                                                     gceTagManager) {
    this.addTag = () => {
      this.command.tags.push({});
    };

    this.removeTag = (index) => {
      this.command.tags.splice(index, 1);
      gceTagManager.updateSelectedTags();
    };

    this.inferSelectedSecurityGroupFromTag = gceTagManager.inferSelectedSecurityGroupFromTag;
    this.showToolTip = gceTagManager.showToolTip;
    this.getToolTipContent = gceTagManager.getToolTipContent;

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

    this.manageMaxUnavailableMetric = (selectedMetric) => {
      if (!selectedMetric) {
        delete this.command.autoHealingPolicy.maxUnavailable;
      } else {
        let toDeleteKey = selectedMetric === 'percent' ? 'fixed' : 'percent';
        _.set(this.command.autoHealingPolicy, ['maxUnavailable', toDeleteKey], undefined);
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
