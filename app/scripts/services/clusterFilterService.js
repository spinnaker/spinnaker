'use strict';

angular
  .module('cluster.filter.service', ['cluster.filter.model'])
  .factory('ClusterFilterService', function ($location, _, clusterFilterModel) {

    function updateQueryParams() {
      $location.search('q',
          clusterFilterModel.filter.length > 0 ? clusterFilterModel.filter : null);
//      $location.search('hideHealth', sortFilter.hideHealthy ? true : null);
//      $location.search('hideInstances', sortFilter.showAllInstances ? null : true);
//      $location.search('hideDisabled', sortFilter.hideDisabled ? true : null);
//      $location.search('primary',
//          sortFilter.sortPrimary===defPrimary ? null: sortFilter.sortPrimary);
//      $location.search('secondary',
//          sortFilter.sortSecondary===defSecondary ? null:sortFilter.sortSecondary);
    }


    function updateClusterGroups(application) {
      var filter = clusterFilterModel.filter.toLowerCase();
//      var groups = [];
//      var totalInstancesDisplayed = 0;
      var serverGroups = filterServerGroupsForDisplay(application.serverGroups, application.cluster, filter);


//      var grouped = _.groupBy(serverGroups, primarySort);

      return serverGroups;

    }

    function filterServerGroupsForDisplay(serverGroups, clusters, filter) {
      return  _.chain(clusters)
        .collect('serverGroups')
        .flatten()
        .filter(function(serverGroup) {
          if (!filter) {
            return true;
          }
          return filter.split(' ').every(function(testWord) {
            return serverGroup.searchField.indexOf(testWord) !== -1;
          });
        })
//        .filter(checkAgainstActiveFilters)
        .value();
    }

    return {
      sortFilter: clusterFilterModel.sortFilter,
      updateQueryParams: updateQueryParams,
      updateClusterGroups: updateClusterGroups,
      filterServerGroupsForDisplay: filterServerGroupsForDisplay
    };
  }
);

