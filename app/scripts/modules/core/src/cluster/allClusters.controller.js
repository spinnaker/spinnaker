'use strict';

import { module } from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';

import { CORE_ACCOUNT_ACCOUNT_MODULE } from '../account/account.module';
import { CloudProviderRegistry, ProviderSelectionService } from '../cloudProvider';
import { CLUSTER_FILTER } from './filter/clusterFilter.component';
import { FILTER_TAGS_COMPONENT } from '../filterModel/filterTags.component';
import { SERVER_GROUP_COMMAND_BUILDER_SERVICE } from '../serverGroup/configure/common/serverGroupCommandBuilder.service';
import { ClusterState } from '../state';
import { noop } from '../utils';
import { CORE_UTILS_WAYPOINTS_WAYPOINTCONTAINER_DIRECTIVE } from '../utils/waypoints/waypointContainer.directive';

import './rollups.less';

export const CORE_CLUSTER_ALLCLUSTERS_CONTROLLER = 'spinnaker.core.cluster.allClusters.controller';
export const name = CORE_CLUSTER_ALLCLUSTERS_CONTROLLER; // for backwards compatibility
module(CORE_CLUSTER_ALLCLUSTERS_CONTROLLER, [
  CLUSTER_FILTER,
  CORE_ACCOUNT_ACCOUNT_MODULE,
  SERVER_GROUP_COMMAND_BUILDER_SERVICE,
  FILTER_TAGS_COMPONENT,
  CORE_UTILS_WAYPOINTS_WAYPOINTCONTAINER_DIRECTIVE,
  ANGULAR_UI_BOOTSTRAP,
]).controller('AllClustersCtrl', [
  '$scope',
  'app',
  '$uibModal',
  '$timeout',
  'insightFilterStateModel',
  'serverGroupCommandBuilder',
  function ($scope, app, $uibModal, $timeout, insightFilterStateModel, serverGroupCommandBuilder) {
    this.$onInit = () => {
      const groupsUpdatedSubscription = ClusterState.filterService.groupsUpdatedStream.subscribe(() =>
        clusterGroupsUpdated(),
      );
      this.application = app;
      ClusterState.filterModel.activate();
      this.initialized = false;
      this.dataSource = app.getDataSource('serverGroups');

      $scope.filterModel = ClusterState.filterModel;
      ProviderSelectionService.isDisabled(app).then((disabled) => {
        $scope.isDisabled = disabled;
      });
      this.createLabel = 'Create Server Group';

      app
        .getDataSource('serverGroups')
        .ready()
        .then(
          () => {
            insightFilterStateModel.filtersHidden = Boolean(this.dataSource.fetchOnDemand);
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

    const updateClusterGroups = () => {
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

    const clusterGroupsUpdated = () => {
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

    this.clearFilters = function () {
      ClusterState.filterService.clearFilters();
      updateClusterGroups();
    };

    this.createServerGroup = function createServerGroup() {
      ProviderSelectionService.selectProvider(app, 'serverGroup')
        .then(function (provider) {
          serverGroupCommandBuilder.buildNewServerGroupCommand(app, provider, null).then((command) => {
            const providerConfig = CloudProviderRegistry.getValue(provider, 'serverGroup');
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
                windowClass: 'modal-z-index',
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
        })
        .catch(noop);
    };

    this.updateClusterGroups = _.debounce(updateClusterGroups, 200);

    this.clustersLoadError = () => {
      this.loadError = true;
      this.initialized = true;
    };
  },
]);
