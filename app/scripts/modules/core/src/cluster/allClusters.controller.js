'use strict';

const angular = require('angular');

import { CLOUD_PROVIDER_REGISTRY } from 'core/cloudProvider/cloudProvider.registry';
import { CLUSTER_FILTER_SERVICE } from 'core/cluster/filter/clusterFilter.service';
import { SERVER_GROUP_COMMAND_BUILDER_SERVICE } from 'core/serverGroup/configure/common/serverGroupCommandBuilder.service';
import { INSIGHT_NGMODULE } from 'core/insight/insight.module';
import { ClusterState } from 'core/state';
import { PROVIDER_SELECTION_SERVICE } from 'core/cloudProvider/providerSelection/providerSelection.service';
import { SKIN_SELECTION_SERVICE } from 'core/cloudProvider/skinSelection/skinSelection.service';

import { CLUSTER_FILTER } from './filter/clusterFilter.component';
import { FILTER_TAGS_COMPONENT } from '../filterModel/filterTags.component';
import { MULTISELECT_MODEL } from './filter/multiselect.model';

import './rollups.less';

module.exports = angular
  .module('spinnaker.core.cluster.allClusters.controller', [
    CLUSTER_FILTER_SERVICE,
    MULTISELECT_MODEL,
    CLUSTER_FILTER,
    require('../account/account.module').name,
    PROVIDER_SELECTION_SERVICE,
    SKIN_SELECTION_SERVICE,
    SERVER_GROUP_COMMAND_BUILDER_SERVICE,
    FILTER_TAGS_COMPONENT,
    require('../utils/waypoints/waypointContainer.directive').name,
    INSIGHT_NGMODULE.name,
    require('angular-ui-bootstrap'),
    CLOUD_PROVIDER_REGISTRY,
  ])
  .controller('AllClustersCtrl', function(
    $scope,
    app,
    $uibModal,
    $timeout,
    providerSelectionService,
    clusterFilterService,
    MultiselectModel,
    insightFilterStateModel,
    serverGroupCommandBuilder,
    cloudProviderRegistry,
    skinSelectionService,
  ) {
    this.$onInit = () => {
      insightFilterStateModel.filtersHidden = true; // hidden to prevent filter flashing for on-demand apps
      const groupsUpdatedSubscription = clusterFilterService.groupsUpdatedStream.subscribe(() =>
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
        MultiselectModel.clearAll();
        insightFilterStateModel.filtersHidden = false;
        groupsUpdatedSubscription.unsubscribe();
      });
    };

    let updateClusterGroups = () => {
      if (app.getDataSource('serverGroups').fetchOnDemand) {
        insightFilterStateModel.filtersHidden = true;
      }
      clusterFilterService.updateClusterGroups(app);
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
      MultiselectModel.syncNavigation();
      updateClusterGroups();
    };

    this.clearFilters = function() {
      clusterFilterService.clearFilters();
      updateClusterGroups();
    };

    this.createServerGroup = function createServerGroup() {
      providerSelectionService.selectProvider(app, 'serverGroup').then(function(selectedProvider) {
        skinSelectionService.selectSkin(selectedProvider).then(function(selectedVersion) {
          let provider = cloudProviderRegistry.getValue(selectedProvider, 'serverGroup', selectedVersion);
          $uibModal.open({
            templateUrl: provider.cloneServerGroupTemplateUrl,
            controller: `${provider.cloneServerGroupController} as ctrl`,
            size: 'lg',
            resolve: {
              title: () => 'Create New Server Group',
              application: () => app,
              serverGroup: () => null,
              serverGroupCommand: () => serverGroupCommandBuilder.buildNewServerGroupCommand(app, selectedProvider),
              provider: () => selectedProvider,
            },
          });
        });
      });
    };

    this.updateClusterGroups = _.debounce(updateClusterGroups, 200);

    this.clustersLoadError = () => {
      this.loadError = true;
      this.initialized = true;
    };
  });
