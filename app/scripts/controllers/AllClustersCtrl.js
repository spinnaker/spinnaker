'use strict';


angular.module('clusters.all', ['cluster.filter.service', 'cluster.filter.model'])
  .controller('AllClustersCtrl', function($scope, application, $modal,
                                          securityGroupService, accountService,
                                          _, $stateParams, $location, settings, $q, $window, clusterFilterService, ClusterFilterModel, serverGroupService) {

    $scope.sortFilter = ClusterFilterModel.sortFilter;

    var sortOptions = ClusterFilterModel.sortFilter.sortOptions;

    this.getSortOptions = function getSortOptions(exclude) {
      return exclude ?
        sortOptions.filter(function(option) { return option.key !== exclude; }) :
        sortOptions;
    };

    // Because we use reloadOnSearch: false for this state, and reloadOnSearch doesn't discriminate parameters,
    // we won't change state when the application changes in the URL via global search or manually changing the URL
    // if the current view is 'clusters'. So we must brutally reload the entire page.
    $scope.$on('$locationChangeStart', function(event, newLocation, currentLocation) {
      var pattern = /applications\/(.+)\/clusters([^\/]?)/;
      var newLocationMatch = pattern.exec(newLocation),
          currentLocationMatch = pattern.exec(currentLocation);
      if (newLocationMatch && currentLocationMatch && newLocationMatch[1] !== currentLocationMatch[1]) {
        $window.location.reload();
      }
    });

    this.updateSorting = function updateSorting() {
      var sortFilter = $scope.sortFilter;
      if (sortFilter.sortPrimary === sortFilter.sortSecondary) {
        sortFilter.sortSecondary = this.getSortOptions(sortFilter.sortPrimary)[0].key;
      }
      this.updateClusterGroups();
    }.bind(this);

    $scope.$watch('sortFilter.sortPrimary', this.updateSorting);

    function addSearchFields() {
      application.clusters.forEach(function(cluster) {
        cluster.serverGroups.forEach(function(serverGroup) {
          if (!serverGroup.searchField) {
            serverGroup.searchField = [
              serverGroup.region.toLowerCase(),
              serverGroup.name.toLowerCase(),
              serverGroup.account.toLowerCase(),
              _.collect(serverGroup.loadBalancers, 'name').join(' '),
              _.collect(serverGroup.instances, 'id').join(' ')
            ].join(' ');
          }
        });
      });
    }


    function updateClusterGroups() {
      clusterFilterService.updateQueryParams();
      $scope.$evalAsync(
        clusterFilterService.updateClusterGroups(application)
      );

      $scope.groups = ClusterFilterModel.groups;
      $scope.displayOptions = ClusterFilterModel.displayOptions;
    }

    this.createServerGroup = function createServerGroup() {
      var provider = $q.when('aws');

      if (settings.providers && settings.providers.length && settings.providers.length > 1) {
        provider = $modal.open({
          templateUrl: 'views/modal/providerSelection.html',
          controller: 'ProviderSelectCtrl as ctrl'
        }).result;
      }

      provider.then(function(selectedProvider) {
        $modal.open({
          templateUrl: 'views/application/modal/serverGroup/' + selectedProvider + '/serverGroupWizard.html',
          controller: selectedProvider + 'CloneServerGroupCtrl as ctrl',
          resolve: {
            title: function() { return 'Create New Server Group'; },
            application: function() { return application; },
            serverGroup: function() { return null; },
            serverGroupCommand: function() { return serverGroupService.buildNewServerGroupCommand(application, selectedProvider); },
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
