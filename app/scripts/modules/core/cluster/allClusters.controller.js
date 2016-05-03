'use strict';

let angular = require('angular');

require('./rollups.less');

module.exports = angular.module('spinnaker.core.cluster.allClusters.controller', [
  require('../cluster/filter/clusterFilter.service'),
  require('../cluster/filter/clusterFilter.model'),
  require('../cluster/filter/multiselect.model'),
  require('../config/settings.js'),
  require('./filter/clusterFilter.controller'),
  require('./clusterPod.directive'),
  require('./categorySelection/categorySelection.service.js'),
  require('../account/account.module'),
  require('../cloudProvider/providerSelection/providerSelection.service'),
  require('../serverGroup/configure/common/serverGroupCommandBuilder'),
  require('../job/configure/common/jobCommandBuilder'),
  require('../filterModel/filter.tags.directive'),
  require('../utils/waypoints/waypointContainer.directive'),
  require('angular-ui-bootstrap'),
  require('../cloudProvider/cloudProvider.registry'),
])
  .controller('AllClustersCtrl', function($scope, app, $uibModal, $timeout, providerSelectionService, _, clusterFilterService,
                                          ClusterFilterModel, MultiselectModel, serverGroupCommandBuilder, cloudProviderRegistry,
                                          categorySelectionService, jobCommandBuilder, settings) {

    ClusterFilterModel.activate();
    this.initialized = false;

    $scope.sortFilter = ClusterFilterModel.sortFilter;

    this.groupingsTemplate = require('./groupings.html');
    this.createLabel = 'Create Server Group';

    if (settings.feature.jobs) {
      this.createLabel = this.createLabel + '/Job';
    }

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

    this.toggleMultiselect = () => {
      ClusterFilterModel.sortFilter.multiselect = !ClusterFilterModel.sortFilter.multiselect;
      MultiselectModel.syncNavigation();
      updateClusterGroups();
    };

    this.clearFilters = function() {
      clusterFilterService.clearFilters();
      updateClusterGroups();
    };

    this.createServerGroup = function createServerGroup() {
      categorySelectionService.selectCategory().then(function(selectedCategory) {
        providerSelectionService.selectProvider(app, selectedCategory).then(function(selectedProvider) {
          let provider = cloudProviderRegistry.getValue(selectedProvider, selectedCategory);
          if (selectedCategory === 'serverGroup') {
            $uibModal.open({
              templateUrl: provider.cloneServerGroupTemplateUrl,
              controller: `${provider.cloneServerGroupController} as ctrl`,
              size: 'lg',
              resolve: {
                title: function() { return 'Create New Server Group'; },
                application: function() { return app; },
                serverGroup: function() { return null; },
                serverGroupCommand: function() { return serverGroupCommandBuilder.buildNewServerGroupCommand(app, selectedProvider); },
                provider: function() { return selectedProvider; }
              }
            });
          } else if (selectedCategory === 'job') {
            $uibModal.open({
              templateUrl: provider.cloneJobTemplateUrl,
              controller: `${provider.cloneJobController} as ctrl`,
              size: 'lg',
              resolve: {
                title: function() { return 'Create New Job'; },
                application: function() { return app; },
                job: function() { return null; },
                jobCommand: function() { return jobCommandBuilder.buildNewJobCommand(app, selectedProvider); },
                provider: function() { return selectedProvider; }
              }
            });
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
      MultiselectModel.clearAll();
    });

  });
