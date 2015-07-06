'use strict';

let angular = require('angular');

module.exports = angular
  .module('cluster.filter.service', [
    'cluster.filter.model',
    require('utils/lodash.js'),
    require('utils/waypoints/waypoint.service.js'),
  ])
  .factory('clusterFilterService', function ($location, ClusterFilterModel, _, waypointService, $log) {

    var lastApplication = null;

    function updateQueryParams() {
      $location.search('q', ClusterFilterModel.sortFilter.filter || null);
      $location.search('hideInstances', ClusterFilterModel.sortFilter.showAllInstances ? null : true);
      $location.search('listInstances', ClusterFilterModel.sortFilter.listInstances ? true : null);
      $location.search('instanceSort',
          ClusterFilterModel.sortFilter.instanceSort.key !== 'launchTime' ? ClusterFilterModel.sortFilter.instanceSort.key : null);

      $location.search('acct', convertTrueModelValuesToArray(ClusterFilterModel.sortFilter.account).join() || null);
      $location.search('reg', convertTrueModelValuesToArray(ClusterFilterModel.sortFilter.region).join() || null);
      $location.search('status', convertTrueModelValuesToArray(ClusterFilterModel.sortFilter.status).join() || null);
      $location.search('providerType', convertTrueModelValuesToArray(ClusterFilterModel.sortFilter.providerType).join() || null);
      $location.search('instanceType', convertTrueModelValuesToArray(ClusterFilterModel.sortFilter.instanceType).join() || null);
      $location.search('zone', convertTrueModelValuesToArray(ClusterFilterModel.sortFilter.availabilityZone).join() || null);
      $location.search('minInstances', coerceIntToString(ClusterFilterModel.sortFilter.minInstances));
      $location.search('maxInstances', coerceIntToString(ClusterFilterModel.sortFilter.maxInstances));

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

    function coerceIntToString(val) {
      if (val !== null && val !== undefined && !isNaN(val)) {
        return val + '';
      }
      return null;
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

    function instanceCountFilter(serverGroup) {
      var shouldInclude = true;
      if (ClusterFilterModel.sortFilter.minInstances && !isNaN(ClusterFilterModel.sortFilter.minInstances)) {
        shouldInclude = serverGroup.instances.length >= ClusterFilterModel.sortFilter.minInstances;
      }
      if (shouldInclude && ClusterFilterModel.sortFilter.maxInstances !== null && !isNaN(ClusterFilterModel.sortFilter.maxInstances)) {
        shouldInclude = serverGroup.instances.length <= ClusterFilterModel.sortFilter.maxInstances;
      }
      return shouldInclude;
    }

    function filterServerGroupsForDisplay(serverGroups, filter) {
      return  _.chain(serverGroups)
        .filter(function(serverGroup) {
          if (!filter) {
            return true;
          }
          if (filter.indexOf('clusters:') !== -1) {
            var clusterNames = filter.split('clusters:')[1].replace(/\s/g, '').split(',');
            return clusterNames.indexOf(serverGroup.cluster) !== -1;
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
        .filter(instanceCountFilter)
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
      if (!application) {
        application = lastApplication;
        if (!lastApplication) {
          return null;
        }
      }

      var groups = [];

      var filter = ClusterFilterModel.sortFilter.filter.toLowerCase();
      var serverGroups = filterServerGroupsForDisplay(application.serverGroups, filter);

      var grouped = _.groupBy(serverGroups, 'account');

      _.forOwn(grouped, function(group, key) {
        var subGroupings = _.groupBy(group, 'cluster'),
          subGroups = [];

        _.forOwn(subGroupings, function(subGroup, subKey) {
          var subGroupings = _.groupBy(subGroup, 'region'),
            subSubGroups = [];

          _.forOwn(subGroupings, function(subSubGroup, subSubKey) {
            subSubGroups.push( { heading: subSubKey, serverGroups: subSubGroup } );
          });
          subGroups.push( { heading: subKey, subgroups: _.sortBy(subSubGroups, 'heading'), cluster: getCluster(application, subKey, key) } );
        });

        groups.push( { heading: key, subgroups: _.sortBy(subGroups, 'heading') } );

      });

      sortGroupsByHeading(groups);
      setDisplayOptions();
      waypointService.restoreToWaypoint(application.name);
      addTags();
      lastApplication = application;
      return groups;
    }

    function getCluster(application, clusterName, account) {
      return _.find(application.clusters, {account: account, name: clusterName });
    }

    function diffSubgroups(oldGroups, newGroups) {
      var groupsToRemove = [];

      oldGroups.forEach(function(oldGroup, idx) {
        var newGroup = _.find(newGroups, { heading: oldGroup.heading });
        if (!newGroup) {
          groupsToRemove.push(idx);
        } else {
          if (newGroup.cluster) {
            oldGroup.cluster = newGroup.cluster;
          }
          if (newGroup.serverGroups) {
            diffServerGroups(oldGroup, newGroup);
          }
          if (newGroup.subgroups) {
            diffSubgroups(oldGroup.subgroups, newGroup.subgroups);
          }
        }
      });
      groupsToRemove.reverse().forEach(function(idx) {
        oldGroups.splice(idx, 1);
      });
      newGroups.forEach(function(newGroup) {
        var match = _.find(oldGroups, { heading: newGroup.heading });
        if (!match) {
          oldGroups.push(newGroup);
        }
      });
    }

    function diffServerGroups(oldGroup, newGroup) {
      var toRemove = [];
      oldGroup.serverGroups.forEach(function(serverGroup, idx) {
        var newServerGroup = _.find(newGroup.serverGroups, { name: serverGroup.name, account: serverGroup.account, region: serverGroup.region });
        if (!newServerGroup) {
          $log.debug('server group no longer found, removing:', serverGroup.name, serverGroup.account, serverGroup.region);
          toRemove.push(idx);
        } else {
          if (serverGroup.stringVal !== newServerGroup.stringVal) {
            $log.debug('change detected, updating server group:', serverGroup.name, serverGroup.account, serverGroup.region);
            oldGroup.serverGroups.splice(idx, 1, newServerGroup);
          }
        }
      });
      toRemove.reverse().forEach(function(idx) {
        oldGroup.serverGroups.splice(idx, 1);
      });
      newGroup.serverGroups.forEach(function(serverGroup) {
        var oldServerGroup = _.find(oldGroup.serverGroups, { name: serverGroup.name, account: serverGroup.account, region: serverGroup.region });
        if (!oldServerGroup) {
          $log.debug('new server group found, adding', serverGroup.name, serverGroup.account, serverGroup.region);
          oldGroup.serverGroups.push(serverGroup);
        }
      });
    }

    function sortGroupsByHeading(groups) {
      diffSubgroups(ClusterFilterModel.groups, groups);

      // sort groups in place so Angular doesn't try to update the world
      ClusterFilterModel.groups.sort(function(a, b) {
        if (a.heading < b.heading) {
          return -1;
        }
        if (a.heading > b.heading) {
          return 1;
        }
        return 0;
      });
    }

    function setDisplayOptions() {
      var newOptions =  {
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

    function addTagsForSection(key, label, translator) {
      translator = translator || {};
      var tags = ClusterFilterModel.sortFilter.tags;
      if (ClusterFilterModel.sortFilter[key]) {
        _.forOwn(ClusterFilterModel.sortFilter[key], function(isActive, value) {
          if (isActive) {
            tags.push({
              key: key,
              label: label,
              value: translator[value] || value,
              clear: function() {
                delete ClusterFilterModel.sortFilter[key][value];
                updateQueryParams();
                updateClusterGroups();
              }
            });
          }
        });
      }
      return tags;
    }

    function addFilterTag() {
      if (ClusterFilterModel.sortFilter.filter) {
        ClusterFilterModel.sortFilter.tags.push({
          key: 'filter',
          label: 'search',
          value: ClusterFilterModel.sortFilter.filter,
          clear: function() {
            ClusterFilterModel.sortFilter.filter = '';
            updateQueryParams();
            updateClusterGroups();
          }
        });
      }
    }

    function addInstanceCountTags() {
      if (ClusterFilterModel.sortFilter.minInstances !== null && !isNaN(ClusterFilterModel.sortFilter.minInstances)) {
        ClusterFilterModel.sortFilter.tags.push({
          key: 'minInstances',
          label: 'instance count (min)',
          value: ClusterFilterModel.sortFilter.minInstances,
          clear: function() {
            ClusterFilterModel.sortFilter.minInstances = undefined;
            updateQueryParams();
            updateClusterGroups();
          }
        });
      }
      if (ClusterFilterModel.sortFilter.maxInstances !== null && !isNaN(ClusterFilterModel.sortFilter.maxInstances)) {
        ClusterFilterModel.sortFilter.tags.push({
          key: 'maxInstances',
          label: 'instance count (max)',
          value: ClusterFilterModel.sortFilter.maxInstances,
          clear: function() {
            ClusterFilterModel.sortFilter.maxInstances = undefined;
            updateQueryParams();
            updateClusterGroups();
          }
        });
      }
    }

    function addTags() {
      var tags = ClusterFilterModel.sortFilter.tags;
      tags.length = 0;
      addFilterTag();
      addInstanceCountTags();
      addTagsForSection('account', 'account');
      addTagsForSection('region', 'region');
      addTagsForSection('status', 'status', {Up: 'Healthy', Down: 'Unhealthy', OutOfService: 'Out of Service'});
      addTagsForSection('availabilityZone', 'zone');
      addTagsForSection('instanceType', 'instance type');
      addTagsForSection('providerType', 'provider');
    }

    return {
      sortFilter: ClusterFilterModel.sortFilter,
      updateQueryParams: updateQueryParams,
      updateClusterGroups: updateClusterGroups,
      filterServerGroupsForDisplay: filterServerGroupsForDisplay,
      setDisplayOptions: setDisplayOptions,
      sortGroupsByHeading: sortGroupsByHeading,
      clearFilters: clearFilters,
      shouldShowInstance: shouldShowInstance,
    };
  }
)
.name;

