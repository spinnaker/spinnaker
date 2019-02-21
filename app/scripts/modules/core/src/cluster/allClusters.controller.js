'use strict';

const angular = require('angular');

import { CloudProviderRegistry } from 'core/cloudProvider';
import { SERVER_GROUP_COMMAND_BUILDER_SERVICE } from 'core/serverGroup/configure/common/serverGroupCommandBuilder.service';
import { INSIGHT_FILTER_COMPONENT } from 'core/insight/insightFilter.component';
import { ClusterState } from 'core/state';
import { PROVIDER_SELECTION_SERVICE } from 'core/cloudProvider/providerSelection/providerSelection.service';
import { SKIN_SELECTION_SERVICE } from 'core/cloudProvider/skinSelection/skinSelection.service';

import { CLUSTER_FILTER } from './filter/clusterFilter.component';
import { FILTER_TAGS_COMPONENT } from '../filterModel/filterTags.component';

import './rollups.less';

module.exports = angular
  .module('spinnaker.core.cluster.allClusters.controller', [
    CLUSTER_FILTER,
    require('../account/account.module').name,
    PROVIDER_SELECTION_SERVICE,
    SKIN_SELECTION_SERVICE,
    SERVER_GROUP_COMMAND_BUILDER_SERVICE,
    FILTER_TAGS_COMPONENT,
    require('../utils/waypoints/waypointContainer.directive').name,
    INSIGHT_FILTER_COMPONENT,
    require('angular-ui-bootstrap'),
  ])
  .controller('AllClustersCtrl', ['$scope', 'app', '$uibModal', '$timeout', 'providerSelectionService', 'insightFilterStateModel', 'serverGroupCommandBuilder', 'skinSelectionService', function(
    $scope,
    app,
    $uibModal,
    $timeout,
    providerSelectionService,
    insightFilterStateModel,
    serverGroupCommandBuilder,
    skinSelectionService,
  ) {
    this.$onInit = () => {
      insightFilterStateModel.filtersHidden = true; // hidden to prevent filter flashing for on-demand apps
      const groupsUpdatedSubscription = ClusterState.filterService.groupsUpdatedStream.subscribe(() =>
        clusterGroupsUpdated(),
      );
      this.application = app;
      ClusterState.filterModel.activate();
      this.initialized = false;
      this.dataSource = app.getDataSource('serverGroups');
      this.application = app;

      $scope.sortFilter = ClusterState.filterModel.sortFilter;

      this.createLabel = 'Create Server Group';

      app
        .getDataSource('serverGroups')
        .ready()
        .then(
          () => {
            insightFilterStateModel.filtersHidden = false;
            updateClusterGroups();
          },
          () => this.clustersLoadError(),
        );

      app.setActiveState(app.serverGroups);
      app.serverGroups.onRefresh($scope, updateClusterGroups);
      $scope.$on('$destroy', () => {
        app.setActiveState();
        ClusterState.multiselectModel.clearAll();
        insightFilterStateModel.filtersHidden = false;
        groupsUpdatedSubscription.unsubscribe();
      });
    };

    let updateClusterGroups = () => {
      if (app.getDataSource('serverGroups').fetchOnDemand) {
        insightFilterStateModel.filtersHidden = true;
      }
      ClusterState.filterService.updateClusterGroups(app);
      clusterGroupsUpdated();
      // Timeout because the updateClusterGroups method is debounced by 25ms
      $timeout(() => {
        this.initialized = true;
      }, 50);
    };

    let clusterGroupsUpdated = () => {
      $scope.$applyAsync(() => {
        $scope.groups = ClusterState.filterModel.groups;
        $scope.tags = ClusterState.filterModel.tags;
      });
    };

    this.toggleMultiselect = () => {
      ClusterState.filterModel.sortFilter.multiselect = !ClusterState.filterModel.sortFilter.multiselect;
      ClusterState.multiselectModel.syncNavigation();
      updateClusterGroups();
    };

    this.syncUrlAndUpdateClusterGroups = () => {
      ClusterState.filterModel.applyParamsToUrl();
      this.updateClusterGroups();
    };

    this.clearFilters = function() {
      ClusterState.filterService.clearFilters();
      updateClusterGroups();
    };

    this.createServerGroup = function createServerGroup() {
      providerSelectionService.selectProvider(app, 'serverGroup').then(function(provider) {
        skinSelectionService.selectSkin(provider).then(function(selected) {
          serverGroupCommandBuilder.buildNewServerGroupCommand(app, provider, null, selected).then(command => {
            let providerConfig = CloudProviderRegistry.getValue(provider, 'serverGroup', selected);
            const title = 'Create New Server Group';
            const serverGroup = null;
            if (providerConfig.CloneServerGroupModal) {
              // React
              providerConfig.CloneServerGroupModal.show({
                title,
                application: app,
                serverGroup,
                command,
                provider,
                isNew: true,
              });
            } else {
              // angular
              $uibModal.open({
                templateUrl: providerConfig.cloneServerGroupTemplateUrl,
                controller: `${providerConfig.cloneServerGroupController} as ctrl`,
                size: 'lg',
                resolve: {
                  title: () => title,
                  application: () => app,
                  serverGroup: () => serverGroup,
                  serverGroupCommand: () => command,
                  provider: () => provider,
                },
              });
            }
          });
        });
      });
    };

    this.updateClusterGroups = _.debounce(updateClusterGroups, 200);

    this.clustersLoadError = () => {
      this.loadError = true;
      this.initialized = true;
    };
  }]);
