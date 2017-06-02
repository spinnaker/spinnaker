'use strict';

const angular = require('angular');

import { CLOUD_PROVIDER_REGISTRY } from 'core/cloudProvider/cloudProvider.registry';
import { CLUSTER_FILTER_SERVICE } from 'core/cluster/filter/clusterFilter.service';
import { CLUSTER_POD_COMPONENT } from 'core/cluster/clusterPod.component';
import { SERVER_GROUP_COMMAND_BUILDER_SERVICE } from 'core/serverGroup/configure/common/serverGroupCommandBuilder.service';
import { CLUSTER_FILTER } from './filter/clusterFilter.component';
import { INSIGHT_NGMODULE } from 'core/insight/insight.module';
import { CLUSTER_FILTER_MODEL } from '../cluster/filter/clusterFilter.model';
import { FILTER_TAGS_COMPONENT } from '../filterModel/filterTags.component';
import { PROVIDER_SELECTION_SERVICE } from 'core/cloudProvider/providerSelection/providerSelection.service';

import './rollups.less';

module.exports = angular.module('spinnaker.core.cluster.allClusters.controller', [
  CLUSTER_FILTER_SERVICE,
  CLUSTER_FILTER_MODEL,
  require('../cluster/filter/multiselect.model'),
  CLUSTER_FILTER,
  CLUSTER_POD_COMPONENT,
  require('../account/account.module'),
  PROVIDER_SELECTION_SERVICE,
  SERVER_GROUP_COMMAND_BUILDER_SERVICE,
  FILTER_TAGS_COMPONENT,
  require('../utils/waypoints/waypointContainer.directive'),
  INSIGHT_NGMODULE.name,
  require('angular-ui-bootstrap'),
  CLOUD_PROVIDER_REGISTRY,
  require('@uirouter/angularjs').default,
])
  .controller('AllClustersCtrl', function($scope, app, $uibModal, $timeout, providerSelectionService, clusterFilterService, $state,
                                          ClusterFilterModel, MultiselectModel, InsightFilterStateModel, serverGroupCommandBuilder, cloudProviderRegistry) {

    this.$onInit = () => {
      InsightFilterStateModel.filtersHidden = true; // hidden to prevent filter flashing for on-demand apps
      const groupsUpdatedSubscription = clusterFilterService.groupsUpdatedStream.subscribe(() => clusterGroupsUpdated());
      this.application = app;
      ClusterFilterModel.activate();
      this.initialized = false;
      this.dataSource = app.getDataSource('serverGroups');
      this.application = app;

      $scope.sortFilter = ClusterFilterModel.sortFilter;

      this.createLabel = 'Create Server Group';

      app.getDataSource('serverGroups').ready().then(
        () => {
          InsightFilterStateModel.filtersHidden = false;
          updateClusterGroups();
        },
        () => this.clustersLoadError()
      );

      app.activeState = app.serverGroups;
      app.serverGroups.onRefresh($scope, updateClusterGroups);
      $scope.$on('$destroy', () => {
        app.activeState = app;
        MultiselectModel.clearAll();
        InsightFilterStateModel.filtersHidden = false;
        groupsUpdatedSubscription.unsubscribe();
      });
    };

    let updateClusterGroups = () => {
      if (app.getDataSource('serverGroups').fetchOnDemand) {
        InsightFilterStateModel.filtersHidden = true;
      }
      clusterFilterService.updateClusterGroups(app);
      clusterGroupsUpdated();
      // Timeout because the updateClusterGroups method is debounced by 25ms
      $timeout(() => { this.initialized = true; }, 50);
    };

    let clusterGroupsUpdated = () => {
      $scope.$applyAsync(() => {
        $scope.groups = ClusterFilterModel.groups;
        $scope.tags = ClusterFilterModel.tags;
      });
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

    this.clustersLoadError = () => {
      this.loadError = true;
      this.initialized = true;
    };

  });
