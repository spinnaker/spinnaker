'use strict';

import { module } from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';
import _ from 'lodash';

import { CloudProviderRegistry } from '../cloudProvider';
import { ProviderSelectionService } from '../cloudProvider/providerSelection/ProviderSelectionService';
import { SETTINGS } from '../config/settings';
import { FirewallLabels } from './label/FirewallLabels';
import { MANAGED_RESOURCE_STATUS_INDICATOR } from '../managed';
import { SecurityGroupState } from '../state';
import { noop } from '../utils';

export const CORE_SECURITYGROUP_ALLSECURITYGROUPSCTRL = 'spinnaker.core.securityGroup.all.controller';
export const name = CORE_SECURITYGROUP_ALLSECURITYGROUPSCTRL; // for backwards compatibility
module(CORE_SECURITYGROUP_ALLSECURITYGROUPSCTRL, [ANGULAR_UI_BOOTSTRAP, MANAGED_RESOURCE_STATUS_INDICATOR]).controller(
  'AllSecurityGroupsCtrl',
  [
    '$scope',
    'app',
    '$uibModal',
    '$timeout',
    function ($scope, app, $uibModal, $timeout) {
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

      const updateSecurityGroups = () => {
        $scope.$evalAsync(() => {
          SecurityGroupState.filterService.updateSecurityGroups(app);
          groupsUpdated();
        });
      };

      const groupsUpdated = () => {
        $scope.$applyAsync(() => {
          $scope.groups = SecurityGroupState.filterModel.groups;
          $scope.tags = SecurityGroupState.filterModel.tags;
          this.initialized = this.initialized || app.securityGroups.loaded;
        });
      };

      this.clearFilters = function () {
        SecurityGroupState.filterService.clearFilters();
        updateSecurityGroups();
      };

      function createSecurityGroupProviderFilterFn(application, account, provider) {
        const sgConfig = provider.securityGroup;
        return (
          sgConfig &&
          (sgConfig.CreateSecurityGroupModal ||
            (sgConfig.createSecurityGroupTemplateUrl && sgConfig.createSecurityGroupController))
        );
      }

      this.createSecurityGroup = function createSecurityGroup() {
        ProviderSelectionService.selectProvider(app, 'securityGroup', createSecurityGroupProviderFilterFn)
          .then((selectedProvider) => {
            const provider = CloudProviderRegistry.getValue(selectedProvider, 'securityGroup');
            const defaultCredentials =
              app.defaultCredentials[selectedProvider] || SETTINGS.providers[selectedProvider].defaults.account;
            const defaultRegion =
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
          })
          .catch(noop);
      };

      this.updateSecurityGroups = _.debounce(updateSecurityGroups, 200);
    },
  ],
);
