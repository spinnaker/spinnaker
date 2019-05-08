'use strict';

import _ from 'lodash';

import { CloudProviderRegistry } from 'core/cloudProvider';
import { SKIN_SELECTION_SERVICE } from 'core/cloudProvider/skinSelection/skinSelection.service';
import { ProviderSelectionService } from 'core/cloudProvider/providerSelection/ProviderSelectionService';
import { SETTINGS } from 'core/config/settings';
import { FirewallLabels } from './label/FirewallLabels';
import { SecurityGroupState } from 'core/state';
import { noop } from 'core/utils';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.securityGroup.all.controller', [SKIN_SELECTION_SERVICE, require('angular-ui-bootstrap')])
  .controller('AllSecurityGroupsCtrl', [
    '$scope',
    'app',
    '$uibModal',
    '$timeout',
    'skinSelectionService',
    function($scope, app, $uibModal, $timeout, skinSelectionService) {
      this.$onInit = () => {
        const groupsUpdatedSubscription = SecurityGroupState.filterService.groupsUpdatedStream.subscribe(() =>
          groupsUpdated(),
        );

        SecurityGroupState.filterModel.activate();

        this.initialized = false;

        $scope.application = app;

        $scope.sortFilter = SecurityGroupState.filterModel.sortFilter;

        app.setActiveState(app.securityGroups);
        $scope.$on('$destroy', () => {
          app.setActiveState();
          groupsUpdatedSubscription.unsubscribe();
        });

        app.securityGroups.onRefresh($scope, () => updateSecurityGroups());
        app.securityGroups.ready().then(() => updateSecurityGroups());
      };

      this.groupingsTemplate = require('./groupings.html');
      this.firewallLabel = FirewallLabels.get('Firewall');

      let updateSecurityGroups = () => {
        $scope.$evalAsync(() => {
          SecurityGroupState.filterService.updateSecurityGroups(app);
          groupsUpdated();
        });
      };

      let groupsUpdated = () => {
        $scope.$applyAsync(() => {
          $scope.groups = SecurityGroupState.filterModel.groups;
          $scope.tags = SecurityGroupState.filterModel.tags;
          this.initialized = this.initialized || app.securityGroups.loaded;
        });
      };

      this.clearFilters = function() {
        SecurityGroupState.filterService.clearFilters();
        updateSecurityGroups();
      };

      this.createSecurityGroup = function createSecurityGroup() {
        ProviderSelectionService.selectProvider(app, 'securityGroup')
          .then(selectedProvider => {
            skinSelectionService.selectSkin(selectedProvider).then(selectedVersion => {
              let provider = CloudProviderRegistry.getValue(selectedProvider, 'securityGroup', selectedVersion);
              var defaultCredentials =
                  app.defaultCredentials[selectedProvider] || SETTINGS.providers[selectedProvider].defaults.account,
                defaultRegion =
                  app.defaultRegions[selectedProvider] || SETTINGS.providers[selectedProvider].defaults.region;
              if (provider.CreateSecurityGroupModal) {
                provider.CreateSecurityGroupModal.show({
                  credentials: defaultCredentials,
                  application: $scope.application,
                  isNew: true,
                });
              } else {
                $uibModal.open({
                  templateUrl: provider.createSecurityGroupTemplateUrl,
                  controller: `${provider.createSecurityGroupController} as ctrl`,
                  size: 'lg',
                  resolve: {
                    securityGroup: () => {
                      return {
                        credentials: defaultCredentials,
                        subnet: 'none',
                        regions: [defaultRegion],
                        vpcId: null,
                        securityGroupIngress: [],
                      };
                    },
                    application: () => {
                      return app;
                    },
                  },
                });
              }
            });
          })
          .catch(noop);
      };

      this.updateSecurityGroups = _.debounce(updateSecurityGroups, 200);
    },
  ]);
