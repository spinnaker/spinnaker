'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.securityGroup.all.controller', [
  require('./filter/securityGroup.filter.service.js'),
  require('./filter/securityGroup.filter.model.js'),
  require('../utils/lodash.js'),
  require('../cloudProvider/providerSelection/providerSelection.service.js'),
  require('../config/settings.js'),
  require('angular-ui-bootstrap'),
  require('../cloudProvider/cloudProvider.registry.js'),
])
  .controller('AllSecurityGroupsCtrl', function($scope, app, $uibModal, _, $timeout,
                                                providerSelectionService, settings, cloudProviderRegistry,
                                                SecurityGroupFilterModel, securityGroupFilterService) {

    SecurityGroupFilterModel.activate();
    this.initialized = false;

    $scope.application = app;

    $scope.sortFilter = SecurityGroupFilterModel.sortFilter;

    this.groupingsTemplate = require('./groupings.html');

    function addSearchFields() {
      app.securityGroups.forEach(function(securityGroup) {
        if (!securityGroup.searchField) {
          securityGroup.searchField = [
            securityGroup.name,
            securityGroup.id,
            securityGroup.accountName,
            securityGroup.region,
            _.pluck(securityGroup.usages.serverGroups, 'name').join(' '),
            _.pluck(securityGroup.usages.loadBalancers, 'name').join(' ')
          ].join(' ');
        }
      });
    }

    this.clearFilters = function() {
      securityGroupFilterService.clearFilters();
      updateSecurityGroups();
    };

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

    this.createSecurityGroup = function createSecurityGroup() {
      providerSelectionService.selectProvider(app, 'securityGroup').then(function(selectedProvider) {
        let provider = cloudProviderRegistry.getValue(selectedProvider, 'securityGroup');
        var defaultCredentials = app.defaultCredentials[selectedProvider] || settings.providers[selectedProvider].defaults.account,
            defaultRegion = app.defaultRegions[selectedProvider] || settings.providers[selectedProvider].defaults.region;
        $uibModal.open({
          templateUrl: provider.createSecurityGroupTemplateUrl,
          controller: `${provider.createSecurityGroupController} as ctrl`,
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

    function autoRefreshHandler() {
      addSearchFields();
      updateSecurityGroups();
    }

    autoRefreshHandler();

    app.registerAutoRefreshHandler(autoRefreshHandler, $scope);
  }
);
