'use strict';

import {CLOUD_PROVIDER_REGISTRY} from 'core/cloudProvider/cloudProvider.registry';
import {CLUSTER_FILTER_SERVICE} from 'core/cluster/filter/clusterFilter.service';
import {CLUSTER_POD_COMPONENT} from 'core/cluster/clusterPod.component';
import {SERVER_GROUP_COMMAND_BUILDER_SERVICE} from 'core/serverGroup/configure/common/serverGroupCommandBuilder.service';

let angular = require('angular');

require('./rollups.less');

module.exports = angular.module('spinnaker.core.cluster.allClusters.controller', [
  CLUSTER_FILTER_SERVICE,
  require('../cluster/filter/clusterFilter.model'),
  require('../cluster/filter/multiselect.model'),
  require('./filter/clusterFilter.controller'),
  CLUSTER_POD_COMPONENT,
  require('../account/account.module'),
  require('../cloudProvider/providerSelection/providerSelection.service'),
  SERVER_GROUP_COMMAND_BUILDER_SERVICE,
  require('../filterModel/filter.tags.directive'),
  require('../utils/waypoints/waypointContainer.directive'),
  require('angular-ui-bootstrap'),
  CLOUD_PROVIDER_REGISTRY,
  require('angular-ui-router'),
])
  .controller('AllClustersCtrl', function($scope, app, $uibModal, $timeout, providerSelectionService, clusterFilterService, $state,
                                          ClusterFilterModel, MultiselectModel, serverGroupCommandBuilder, cloudProviderRegistry) {

    if (app.serverGroups.disabled) {
      $state.go('^.^' + app.dataSources.find(ds => ds.sref && !ds.disabled).sref, {}, {location: 'replace'});
      return;
    }

    ClusterFilterModel.activate();
    this.initialized = false;

    $scope.sortFilter = ClusterFilterModel.sortFilter;

    this.groupingsTemplate = require('./groupings.html');
    this.createLabel = 'Create Server Group';

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
      providerSelectionService.selectProvider(app, 'serverGroup').then(function(selectedProvider) {
        let provider = cloudProviderRegistry.getValue(selectedProvider, 'serverGroup');
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
