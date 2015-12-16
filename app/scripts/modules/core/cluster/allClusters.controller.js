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

    function addSearchFields() {
      app.serverGroups.forEach(function(serverGroup) {
        var buildInfo = '';
        if (serverGroup.buildInfo && serverGroup.buildInfo.jenkins) {
          buildInfo = [
              '#' + serverGroup.buildInfo.jenkins.number,
              serverGroup.buildInfo.jenkins.host,
              serverGroup.buildInfo.jenkins.name].join(' ').toLowerCase();
        }
        if (!serverGroup.searchField) {
          serverGroup.searchField = [
            serverGroup.region.toLowerCase(),
            serverGroup.name.toLowerCase(),
            serverGroup.account.toLowerCase(),
            buildInfo,
            _.pluck(serverGroup.loadBalancers, 'name').join(' '),
            _.pluck(serverGroup.instances, 'id').join(' ')
          ].join(' ');
        }
      });
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

    function autoRefreshHandler() {
      addSearchFields();
      updateClusterGroups();
    }

    autoRefreshHandler();

    app.registerAutoRefreshHandler(autoRefreshHandler, $scope);
  });
