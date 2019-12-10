'use strict';

const angular = require('angular');

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_ADVANCEDSETTINGSSELECTOR_DIRECTIVE =
  'spinnaker.google.serverGroup.configure.wizard.advancedSettings.selector.directive';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_ADVANCEDSETTINGSSELECTOR_DIRECTIVE; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_ADVANCEDSETTINGSSELECTOR_DIRECTIVE, [
    require('ui-select'),
    require('../securityGroups/tagManager.service').name,
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
  .controller('gceServerGroupAdvancedSettingsSelectorCtrl', [
    '$scope',
    'gceTagManager',
    function($scope, gceTagManager) {
      this.addTag = () => {
        this.command.tags.push({});
      };

      this.removeTag = index => {
        this.command.tags.splice(index, 1);
        gceTagManager.updateSelectedTags();
      };

      this.setDisks = disks => {
        this.command.disks = disks;
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

      this.setEnableVtpm = () => {
        if (!this.command.enableVtpm) {
          // Integrity monitoring requires vTPM to be enabled.
          this.command.enableIntegrityMonitoring = false;
        }
      };

      this.setAcceleratorConfigs = configs => {
        $scope.$apply(() => {
          this.command.acceleratorConfigs = configs;
        });
      };
    },
  ]);
