'use strict';

let angular = require('angular');

require('./rollups.less');

module.exports = angular.module('spinnaker.core.cluster.allClusters.controller', [
  require('../cluster/filter/clusterFilter.service.js'),
  require('../cluster/filter/clusterFilter.model.js'),
  require('./filter/clusterFilter.controller.js'),
  require('./clusterPod.directive.js'),
  require('../account/account.module.js'),
  require('../cloudProvider/providerSelection/providerSelection.service.js'),
  require('../serverGroup/configure/common/serverGroupCommandBuilder.js'),
  require('../filterModel/filter.tags.directive.js'),
  require('../utils/waypoints/waypointContainer.directive.js'),
  require('angular-ui-bootstrap'),
  require('../cloudProvider/cloudProvider.registry.js'),
])
  .controller('AllClustersCtrl', function($scope, app, $uibModal, $timeout, providerSelectionService, _, clusterFilterService,
                                          ClusterFilterModel, serverGroupCommandBuilder, cloudProviderRegistry) {

    ClusterFilterModel.activate();
    this.initialized = false;

    $scope.sortFilter = ClusterFilterModel.sortFilter;

    this.groupingsTemplate = require('./groupings.html');

    let updateClusterGroups = () => {
      ClusterFilterModel.applyParamsToUrl();
      $scope.$evalAsync(() => {
          clusterFilterService.updateClusterGroups(app);
          $scope.groups = ClusterFilterModel.groups;
          $scope.tags = ClusterFilterModel.tags;
          // Timeout because the updateClusterGroups method is debounced by 25ms
          $timeout(() => { this.initialized = true; }, 50);
        }
      );
    };

    this.clearFilters = function() {
      clusterFilterService.clearFilters();
      updateClusterGroups();
    };

    this.createServerGroup = function createServerGroup() {
      providerSelectionService.selectProvider(app, 'serverGroup').then(function(selectedProvider) {
        let provider = cloudProviderRegistry.getValue(selectedProvider, 'serverGroup');
        $uibModal.open({
          templateUrl: provider.cloneServerGroupTemplateUrl,
          controller: `${provider.cloneServerGroupController} as ctrl`,
          size: cloudProviderRegistry.getValue(selectedProvider, 'v2wizard') ? 'lg' : 'md',
          resolve: {
            title: function() { return 'Create New Server Group'; },
            application: function() { return app; },
            serverGroup: function() { return null; },
            serverGroupCommand: function() { return serverGroupCommandBuilder.buildNewServerGroupCommand(app, selectedProvider); },
            provider: function() { return selectedProvider; }
          }
        });
      });
    };

    this.updateClusterGroups = _.debounce(updateClusterGroups, 200);

    if (app.serverGroups.loaded) {
      updateClusterGroups();
    }

    app.activeState = app.serverGroups;
    app.serverGroups.onRefresh($scope, updateClusterGroups);
    $scope.$on('$destroy', () => {
      app.activeState = app;
    });

  });
