'use strict';

angular
  .module('cluster.filter.service', [
    'cluster.filter.model',
    'deckApp.utils.lodash',
    'deckApp.utils.waypoints.service',
  ])
  .factory('clusterFilterService', function ($location, $stateParams, ClusterFilterModel, _, waypointService) {

    function updateQueryParams() {

      var filter = ClusterFilterModel.sortFilter.filter.length ? ClusterFilterModel.sortFilter.filter : null,
          locationQ = $location.search().q || null;
      if (filter !== locationQ) {
        $location.search('q',
            ClusterFilterModel.sortFilter.filter.length > 0 ? ClusterFilterModel.sortFilter.filter : '');
      }
      $location.search('hideInstances', ClusterFilterModel.sortFilter.showAllInstances ? null : true);
      $location.search('listInstances', ClusterFilterModel.sortFilter.listInstances ? 'true' : null);
      $location.search('instanceSort',
          ClusterFilterModel.sortFilter.instanceSort.key);

      updateAccountParams();
      updateRegionParams();
      updateStatusParams();
      updateProviderTypeParams();
      updateInstanceTypeParams();
      updateZoneParams();


    }

    function updateAccountParams() {
      var acct = convertTrueModelValuesToArray(ClusterFilterModel.sortFilter.account);
      $location.search('acct', acct.length ? acct.join() : null);
    }

    function updateRegionParams() {
      var reg = convertTrueModelValuesToArray(ClusterFilterModel.sortFilter.region);
      $location.search('reg', reg.length ? reg.join() : null);
    }

    function updateStatusParams() {
      var status = convertTrueModelValuesToArray(ClusterFilterModel.sortFilter.status);
      $location.search('status', status.length ? status.join() : null);
    }

    function updateProviderTypeParams() {
      var providerTypes = convertTrueModelValuesToArray(ClusterFilterModel.sortFilter.providerType);
      $location.search('providerType', providerTypes.length ? providerTypes.join() : null);
    }

    function updateInstanceTypeParams() {
      var instanceTypes = convertTrueModelValuesToArray(ClusterFilterModel.sortFilter.instanceType);
      $location.search('instanceType', instanceTypes.length ? instanceTypes.join() : null);
    }

    function updateZoneParams() {
      var zones = convertTrueModelValuesToArray(ClusterFilterModel.sortFilter.availabilityZone);
      $location.search('zone', zones.length ? zones.join() : null);
    }

    function convertTrueModelValuesToArray(modelObject) {
      var result = [];
      _.forOwn(modelObject, function (value, key) {
        if (value) {
          result.push(key);
        }
      });
      return result;
    }

    function incrementTotalInstancesDisplayed(totalInstancesDisplayed, serverGroups) {
      return serverGroups
        .reduce(function(total, serverGroup) {
          return serverGroup.instances.length + total;
        }, totalInstancesDisplayed);
    }


    function isFilterable(sortFilterModel) {
      return _.size(sortFilterModel) > 0 && _.any(sortFilterModel);
    }

    function getCheckValues(sortFilterModel) {
      return  _.reduce(sortFilterModel, function(acc, val, key) {
        if (val) {
          acc.push(key);
        }
        return acc;
      }, []);
    }

    function checkAccountFilters(serverGroup) {
      if(isFilterable(ClusterFilterModel.sortFilter.account)) {
        var checkedAccounts = getCheckValues(ClusterFilterModel.sortFilter.account);
        return _.contains(checkedAccounts, serverGroup.account);
      } else {
        return true;
      }
    }

    function checkRegionFilters(serverGroup) {
      if(isFilterable(ClusterFilterModel.sortFilter.region)) {
        var checkedRegions = getCheckValues(ClusterFilterModel.sortFilter.region);
        return _.contains(checkedRegions, serverGroup.region);
      } else {
        return true;
      }
    }

    function checkStatusFilters(serverGroup) {
      if(isFilterable(ClusterFilterModel.sortFilter.status)) {
        var checkedStatus = getCheckValues(ClusterFilterModel.sortFilter.status);
        return _.contains(checkedStatus, 'Up') && serverGroup.downCount === 0 ||
               _.contains(checkedStatus, 'Down') && serverGroup.downCount > 0 ||
               _.contains(checkedStatus, 'OutOfService') && serverGroup.outOfServiceCount > 0 ||
               _.contains(checkedStatus, 'Starting') && serverGroup.startingCount > 0 ||
               _.contains(checkedStatus, 'Disabled') && serverGroup.isDisabled;
      } else {
        return true;
      }
    }

    function providerTypeFilters(serverGroup) {
      if(isFilterable(ClusterFilterModel.sortFilter.providerType)) {
        var checkedProviderTypes = getCheckValues(ClusterFilterModel.sortFilter.providerType);
        return _.contains(checkedProviderTypes, serverGroup.type);
      } else {
        return true;
      }
    }

    function instanceTypeFilters(serverGroup) {
      if(isFilterable(ClusterFilterModel.sortFilter.instanceType)) {
        var checkedInstanceTypes = getCheckValues(ClusterFilterModel.sortFilter.instanceType);
        return _.contains(checkedInstanceTypes, serverGroup.instanceType);
      } else {
        return true;
      }
    }

    function filterServerGroupsForDisplay(clusters, filter) {
      return  _.chain(clusters)
        .collect('serverGroups')
        .flatten()
        .filter(function(serverGroup) {
          if (!filter) {
            return true;
          }

          if(filter.indexOf('cluster:') !== -1) {
              var clusterName = /cluster:([\w-]*)/.exec(filter);
              return serverGroup.cluster === clusterName[1];
          } else {
            return filter.split(' ').every(function(testWord) {
              return serverGroup.searchField.indexOf(testWord) !== -1;
            });
          }
        })
        .filter(checkAccountFilters)
        .filter(checkRegionFilters)
        .filter(checkStatusFilters)
        .filter(providerTypeFilters)
        .filter(instanceTypeFilters)
        .filter(instanceFilters)
        .value();
    }

    function instanceFilters(serverGroup) {
      return !shouldFilterInstances() || _.some(serverGroup.instances, shouldShowInstance);
    }

    function shouldFilterInstances() {
      return isFilterable(ClusterFilterModel.sortFilter.availabilityZone) ||
        (isFilterable(ClusterFilterModel.sortFilter.status) && !ClusterFilterModel.sortFilter.status.hasOwnProperty('Disabled'));
    }

    function shouldShowInstance(instance) {
      if(isFilterable(ClusterFilterModel.sortFilter.availabilityZone)) {
        var checkedAvailabilityZones = getCheckValues(ClusterFilterModel.sortFilter.availabilityZone);
        if (checkedAvailabilityZones.indexOf(instance.availabilityZone) === -1) {
          return false;
        }
      }
      if(isFilterable(ClusterFilterModel.sortFilter.status)) {
        var allCheckedValues = getCheckValues(ClusterFilterModel.sortFilter.status);
        var checkedStatus = _.without(allCheckedValues, 'Disabled');
        if (!checkedStatus.length) {
          return true;
        }
        if (ClusterFilterModel.sortFilter.status.Disabled) {
          // filtering should be performed on the server group; always show instances
          return true;
        }
        return _.contains(checkedStatus, instance.healthState);
      }
      return true;
    }

    function updateClusterGroups(application) {
        var groups = [],
          totalInstancesDisplayed = 0,
          primarySort = ClusterFilterModel.sortFilter.sortPrimary,
          secondarySort = ClusterFilterModel.sortFilter.sortSecondary,
          tertiarySort = ClusterFilterModel.sortFilter.sortOptions.filter(function(option) { return option.key !== primarySort && option.key !== secondarySort; })[0].key;

        var filter = ClusterFilterModel.sortFilter.filter.toLowerCase();
        var serverGroups = filterServerGroupsForDisplay(application.clusters, filter);

        var grouped = _.groupBy(serverGroups, primarySort);

        _.forOwn(grouped, function(group, key) {
          var subGroupings = _.groupBy(group, secondarySort),
            subGroups = [];

          _.forOwn(subGroupings, function(subGroup, subKey) {
            var subGroupings = _.groupBy(subGroup, tertiarySort),
              subSubGroups = [];

            _.forOwn(subGroupings, function(subSubGroup, subSubKey) {
              totalInstancesDisplayed = incrementTotalInstancesDisplayed(totalInstancesDisplayed, subSubGroup);
              subSubGroups.push( { heading: subSubKey, serverGroups: subSubGroup } );
            });
            subGroups.push( { heading: subKey, subgroups: _.sortBy(subSubGroups, 'heading') } );
          });

          groups.push( { heading: key, subgroups: _.sortBy(subGroups, 'heading') } );

        });

        sortGroupsByHeading(groups);
        setDisplayOptions(totalInstancesDisplayed);
        waypointService.restoreToWaypoint(application.name);
        return groups;
    }

    function sortGroupsByHeading(groups) {
      var sortedGroups = _.sortBy(groups, 'heading');
      ClusterFilterModel.groups.length = 0;
      sortedGroups.forEach(function(group) {
        ClusterFilterModel.groups.push(group);
      });
    }

    function setDisplayOptions(totalInstancesDisplayed) {
      var newOptions =  {
        renderInstancesOnScroll: totalInstancesDisplayed > 1000, // TODO: move to config
        totalInstancesDisplayed: totalInstancesDisplayed,
        showInstances: ClusterFilterModel.sortFilter.showAllInstances,
        listInstances: ClusterFilterModel.sortFilter.listInstances,
        hideHealthy: ClusterFilterModel.sortFilter.hideHealthy,
        filter: ClusterFilterModel.sortFilter.filter,
      };
      angular.copy(newOptions, ClusterFilterModel.displayOptions);
    }

    function clearFilters() {
      ClusterFilterModel.clearFilters();
      updateQueryParams();
    }

    return {
      sortFilter: ClusterFilterModel.sortFilter,
      updateQueryParams: updateQueryParams,
      updateClusterGroups: updateClusterGroups,
      filterServerGroupsForDisplay: filterServerGroupsForDisplay,
      incrementTotalInstancesDisplayed: incrementTotalInstancesDisplayed,
      setDisplayOptions: setDisplayOptions,
      sortGroupsByHeading: sortGroupsByHeading,
      clearFilters: clearFilters,
      shouldShowInstance: shouldShowInstance,
    };
  }
);

