'use strict';

let angular = require('angular');

module.exports = angular
  .module('cluster.filter.service', [
    require('angular-ui-router'),
    require('exports?"debounce"!angular-debounce'),
    require('./clusterFilter.model.js'),
    require('../../utils/lodash.js'),
    require('../../utils/waypoints/waypoint.service.js'),
    require('../../filterModel/filter.model.service.js'),
  ])
  .factory('clusterFilterService', function (ClusterFilterModel, _, waypointService, $log, $stateParams, filterModelService, debounce) {

    var lastApplication = null;

    /**
     * Filtering logic
     */

    var isFilterable = filterModelService.isFilterable;

    function instanceTypeFilters(serverGroup) {
      if(isFilterable(ClusterFilterModel.sortFilter.instanceType)) {
        var checkedInstanceTypes = filterModelService.getCheckValues(ClusterFilterModel.sortFilter.instanceType);
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

    function textFilter(serverGroup) {
      var filter = ClusterFilterModel.sortFilter.filter.toLowerCase();
      if (!filter) {
        return true;
      }
      if (filter.indexOf('clusters:') !== -1) {
        var clusterNames = filter.split('clusters:')[1].replace(/\s/g, '').split(',');
        return clusterNames.indexOf(serverGroup.cluster) !== -1;
      }

      if(filter.indexOf('vpc:') !== -1) {
        let [, vpcName] = filter.split('vpc:');
        return serverGroup.vpcName.toLowerCase() === vpcName.toLowerCase();
      }

      if (filter.indexOf('detail:') !== -1) {
        let [, detailName] = filter.split('detail:');
        return serverGroup.detail === detailName.toLowerCase();
      }

      if(filter.indexOf('cluster:') !== -1) {
        let [, clusterName] = filter.split('cluster:');
        return serverGroup.cluster === clusterName;
      } else {
        return filter.split(' ').every(function(testWord) {
          return serverGroup.searchField.indexOf(testWord) !== -1;
        });
      }
    }

    function filterServerGroupsForDisplay(serverGroups) {
      return  _.chain(serverGroups)
        .filter(textFilter)
        .filter(instanceCountFilter)
        .filter(filterModelService.checkAccountFilters(ClusterFilterModel))
        .filter(filterModelService.checkRegionFilters(ClusterFilterModel))
        .filter(filterModelService.checkStackFilters(ClusterFilterModel))
        .filter(filterModelService.checkStatusFilters(ClusterFilterModel))
        .filter(filterModelService.checkProviderFilters(ClusterFilterModel))
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
        var checkedAvailabilityZones = filterModelService.getCheckValues(ClusterFilterModel.sortFilter.availabilityZone);
        if (checkedAvailabilityZones.indexOf(instance.availabilityZone) === -1) {
          return false;
        }
      }
      if(isFilterable(ClusterFilterModel.sortFilter.status)) {
        var allCheckedValues = filterModelService.getCheckValues(ClusterFilterModel.sortFilter.status);
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

    function hasDiscovery(group) {
      return group.serverGroups.some((serverGroup) =>
          (serverGroup.instances || []).some((instance) =>
              (instance.health || []).some((health) => health.type === 'Discovery')
          )
      );
    }

    function hasLoadBalancers(group) {
      return group.serverGroups.some((serverGroup) =>
          (serverGroup.instances || []).some((instance) =>
              (instance.health || []).some((health) => health.type === 'LoadBalancer')
          )
      );
    }

    function addHealthFlags() {
      ClusterFilterModel.groups.forEach((group) => {
        group.subgroups.forEach((subgroup) => {
          subgroup.hasDiscovery = subgroup.subgroups.some(hasDiscovery);
          subgroup.hasLoadBalancers = subgroup.subgroups.some(hasLoadBalancers);
        });
      });
    }

    /**
     * Grouping logic
     */
    // this gets called every time the URL changes, so we debounce it a tiny bit
    var updateClusterGroups = debounce((application) => {
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
      waypointService.restoreToWaypoint(application.name);
      ClusterFilterModel.addTags();
      lastApplication = application;
      addHealthFlags();
      return groups;
    }, 25);

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
            oldGroup.serverGroups[idx] = newServerGroup;
          }
          serverGroup.executions = newServerGroup.executions;
          serverGroup.runningTasks = newServerGroup.runningTasks;
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


    /**
     * Utility method used to navigate to a specific cluster by setting filters
     */
    function overrideFiltersForUrl(result) {
      if (result.href.indexOf('/clusters') !== -1) {
        ClusterFilterModel.clearFilters();
        ClusterFilterModel.sortFilter.filter = result.serverGroup ? result.serverGroup :
          result.cluster ? 'cluster:' + result.cluster : '';
        if (result.account) {
          var acct = {};
          acct[result.account] = true;
          ClusterFilterModel.sortFilter.account = acct;
        }
        if (result.region) {
          var reg = {};
          reg[result.region] = true;
          ClusterFilterModel.sortFilter.region = reg;
        }
        if (result.stack) {
          var stack = {};
          stack[result.stack] = true;
          ClusterFilterModel.sortFilter.stack = stack;
        }
        if (result.detail) {
          var detail = {};
          detail[result.detail] = true;
          ClusterFilterModel.sortFilter.detail = detail;
        }
        if ($stateParams.application === result.application) {
          updateClusterGroups();
        }
      }
    }

    function clearFilters() {
      ClusterFilterModel.clearFilters();
      ClusterFilterModel.applyParamsToUrl();
    }

    return {
      updateClusterGroups: updateClusterGroups,
      filterServerGroupsForDisplay: filterServerGroupsForDisplay,
      sortGroupsByHeading: sortGroupsByHeading,
      clearFilters: clearFilters,
      shouldShowInstance: shouldShowInstance,
      overrideFiltersForUrl: overrideFiltersForUrl,
    };
  }
);

