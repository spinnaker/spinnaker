'use strict';


angular.module('clusters.all', [
  'cluster.filter.service',
  'cluster.filter.model',
  'deckApp.cluster.pod',
  'deckApp.account',
  'deckApp.providerSelection',
  'deckApp.providerSelection.service',
  'deckApp.securityGroup.read.service',
  'deckApp.serverGroup.configure.common.service',
  'deckApp.utils.waypoints.container.directive',
])
  .controller('AllClustersCtrl', function($scope, application, $modal, $location,
                                          securityGroupReader, accountService, providerSelectionService,
                                          _, $stateParams, settings, $q, $window, clusterFilterService, ClusterFilterModel, serverGroupCommandBuilder) {

    $scope.sortFilter = ClusterFilterModel.sortFilter;

    var searchCache = null;

    $scope.$on('$stateChangeStart', function() {
      searchCache = $location.search();
    });
    $scope.$on('$stateChangeSuccess', function() {
      if (searchCache) {
        $location.search(searchCache);
      }
    });

    function addSearchFields() {
      application.clusters.forEach(function(cluster) {
        cluster.serverGroups.forEach(function(serverGroup) {
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
              _.collect(serverGroup.loadBalancers, 'name').join(' '),
              _.collect(serverGroup.instances, 'id').join(' ')
            ].join(' ');
          }
        });
      });
    }

    function updateClusterGroups() {
      clusterFilterService.updateQueryParams();
      $scope.$evalAsync(function() {
          clusterFilterService.updateClusterGroups(application);
        }
      );

      $scope.groups = ClusterFilterModel.groups;
      $scope.displayOptions = ClusterFilterModel.displayOptions;
      $scope.tags = ClusterFilterModel.sortFilter.tags;
    }

    this.clearFilters = function() {
      clusterFilterService.clearFilters();
      updateClusterGroups();
    };

    this.createServerGroup = function createServerGroup() {
      providerSelectionService.selectProvider().then(function(selectedProvider) {
        $modal.open({
          templateUrl: 'scripts/modules/serverGroups/configure/' + selectedProvider + '/wizard/serverGroupWizard.html',
          controller: selectedProvider + 'CloneServerGroupCtrl as ctrl',
          resolve: {
            title: function() { return 'Create New Server Group'; },
            application: function() { return application; },
            serverGroup: function() { return null; },
            serverGroupCommand: function() { return serverGroupCommandBuilder.buildNewServerGroupCommand(application, selectedProvider); },
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

    application.registerAutoRefreshHandler(autoRefreshHandler, $scope);
  }
);
