'use strict';

import _ from 'lodash';

import {CLOUD_PROVIDER_REGISTRY} from 'core/cloudProvider/cloudProvider.registry';
import {SETTINGS} from 'core/config/settings';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.securityGroup.all.controller', [
  require('./filter/securityGroup.filter.service.js'),
  require('./filter/securityGroup.filter.model.js'),
  require('../cloudProvider/providerSelection/providerSelection.service.js'),
  require('angular-ui-bootstrap'),
  CLOUD_PROVIDER_REGISTRY,
])
  .controller('AllSecurityGroupsCtrl', function($scope, app, $uibModal, $timeout,
                                                providerSelectionService, cloudProviderRegistry,
                                                SecurityGroupFilterModel, securityGroupFilterService) {

    SecurityGroupFilterModel.activate();
    this.initialized = false;

    $scope.application = app;

    $scope.sortFilter = SecurityGroupFilterModel.sortFilter;

    this.groupingsTemplate = require('./groupings.html');

    let updateSecurityGroups = () => {
      SecurityGroupFilterModel.applyParamsToUrl();
      $scope.$evalAsync(() => {
        securityGroupFilterService.updateSecurityGroups(app);
        $scope.groups = SecurityGroupFilterModel.groups;
        $scope.tags = SecurityGroupFilterModel.tags;
        // Timeout because the updateSecurityGroups method is debounced by 25ms
        $timeout(() => { this.initialized = true; }, 50);
      });
    };

    this.clearFilters = function() {
      securityGroupFilterService.clearFilters();
      updateSecurityGroups();
    };

    this.createSecurityGroup = function createSecurityGroup() {
      providerSelectionService.selectProvider(app, 'securityGroup').then(function(selectedProvider) {
        let provider = cloudProviderRegistry.getValue(selectedProvider, 'securityGroup');
        var defaultCredentials = app.defaultCredentials[selectedProvider] || SETTINGS.providers[selectedProvider].defaults.account,
            defaultRegion = app.defaultRegions[selectedProvider] || SETTINGS.providers[selectedProvider].defaults.region;
        $uibModal.open({
          templateUrl: provider.createSecurityGroupTemplateUrl,
          controller: `${provider.createSecurityGroupController} as ctrl`,
          size: 'lg',
          resolve: {
            securityGroup: function () {
              return {
                credentials: defaultCredentials,
                subnet: 'none',
                regions: [defaultRegion],
                vpcId: null,
                securityGroupIngress: []
              };
            },
            application: function () {
              return app;
            }
          }
        });
      });
    };

    this.updateSecurityGroups = _.debounce(updateSecurityGroups, 200);

    let handleRefresh = () => {
      this.updateSecurityGroups();
    };

    handleRefresh();

    app.activeState = app.securityGroups;
    $scope.$on('$destroy', () => app.activeState = app);

    app.securityGroups.onRefresh($scope, handleRefresh);
  }
);
