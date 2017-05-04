'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.loadBalancer.filter.service', [
    require('./loadBalancer.filter.model.js'),
    require('../../utils/waypoints/waypoint.service.js'),
    require('../../filterModel/filter.model.service.js'),
  ])
  .factory('loadBalancerFilterService', function (LoadBalancerFilterModel, waypointService, filterModelService,
                                                  $log) {

    var isFilterable = filterModelService.isFilterable,
        getCheckValues = filterModelService.getCheckValues;

    var lastApplication = null;

    function addSearchFields(loadBalancer) {
      if (!loadBalancer.searchField) {
        loadBalancer.searchField = [
          loadBalancer.name,
          loadBalancer.region.toLowerCase(),
          loadBalancer.account,
          _.map(loadBalancer.serverGroups, 'name').join(' '),
          _.map(loadBalancer.instances, 'id').join(' '),
        ].join(' ');
      }
    }

    function checkSearchTextFilter(loadBalancer) {
      var filter = LoadBalancerFilterModel.sortFilter.filter;
      if (!filter) {
        return true;
      }

      if (filter.includes('vpc:')) {
        let [, vpcName] = /vpc:([\w-]*)/.exec(filter);
        return loadBalancer.vpcName.toLowerCase() === vpcName.toLowerCase();
      }
      addSearchFields(loadBalancer);
      return filter.split(' ').every(function(testWord) {
        return loadBalancer.searchField.includes(testWord);
      });
    }

    function filterLoadBalancersForDisplay(loadBalancers) {
      return _.chain(loadBalancers)
        .filter(checkSearchTextFilter)
        .filter(filterModelService.checkAccountFilters(LoadBalancerFilterModel))
        .filter(filterModelService.checkRegionFilters(LoadBalancerFilterModel))
        .filter(filterModelService.checkStackFilters(LoadBalancerFilterModel))
        .filter(filterModelService.checkStatusFilters(LoadBalancerFilterModel))
        .filter(filterModelService.checkProviderFilters(LoadBalancerFilterModel))
        .filter(instanceFilters)
        .value();
    }

    function filterServerGroups(loadBalancer) {
      if (shouldFilterInstances()) {
        return loadBalancer.serverGroups.filter(function(serverGroup) {
          return serverGroup.instances.some(shouldShowInstance);
        });
      }
      return loadBalancer.serverGroups;
    }


    function instanceFilters(loadBalancer) {
      return !shouldFilterInstances() || _.some(loadBalancer.instances, shouldShowInstance);
    }

    function shouldFilterInstances() {
      return isFilterable(LoadBalancerFilterModel.sortFilter.status) ||
        isFilterable(LoadBalancerFilterModel.sortFilter.availabilityZone);
    }

    function shouldShowInstance(instance) {
      if (isFilterable(LoadBalancerFilterModel.sortFilter.availabilityZone)) {
        var checkedAvailabilityZones = getCheckValues(LoadBalancerFilterModel.sortFilter.availabilityZone);
        if (!checkedAvailabilityZones.includes(instance.zone)) {
          return false;
        }
      }
      if (isFilterable(LoadBalancerFilterModel.sortFilter.status)) {
        var allCheckedValues = getCheckValues(LoadBalancerFilterModel.sortFilter.status);
        var checkedStatus = _.without(allCheckedValues, 'Disabled');
        if (!checkedStatus.length) {
          return true;
        }
        return _.includes(checkedStatus, instance.healthState);
      }
      return true;
    }

    /**
     * Grouping
     * @param application
     * @returns {*}
     */
    var updateLoadBalancerGroups = _.debounce((application) => {
      if (!application) {
        application = lastApplication;
        if (!lastApplication) {
          return null;
        }
      }

      var groups = [];

      var filter = LoadBalancerFilterModel.sortFilter.filter.toLowerCase();
      var loadBalancers = filterLoadBalancersForDisplay(application.loadBalancers.data, filter);

      var grouped = _.groupBy(loadBalancers, 'account');

      _.forOwn(grouped, function(group, key) {
        var subGroupings = _.groupBy(group, 'name'),
          subGroups = [];

        _.forOwn(subGroupings, function(subGroup, subKey) {
          var subSubGroups = [];
          subGroup.forEach(function(loadBalancer) {
            subSubGroups.push({
              heading: loadBalancer.region,
              loadBalancer: loadBalancer,
              serverGroups: filterServerGroups(loadBalancer),
            });
          });
          subGroups.push( {
            heading: subKey,
            subgroups: _.sortBy(subSubGroups, 'heading'),
          });
        });

        groups.push( { heading: key, subgroups: _.sortBy(subGroups, 'heading') } );

      });

      sortGroupsByHeading(groups);
      waypointService.restoreToWaypoint(application.name);
      LoadBalancerFilterModel.addTags();
      lastApplication = application;
      return groups;
    }, 25);

    function diffSubgroups(oldGroups, newGroups) {
      var groupsToRemove = [];

      oldGroups.forEach(function(oldGroup, idx) {
        var newGroup = _.find(newGroups, { heading: oldGroup.heading });
        if (!newGroup) {
          groupsToRemove.push(idx);
        } else {
          if (newGroup.loadBalancer) {
            oldGroup.loadBalancer = newGroup.loadBalancer;
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
        serverGroup.stringVal = serverGroup.stringVal || angular.toJson(serverGroup);
        var newServerGroup = _.find(newGroup.serverGroups, { name: serverGroup.name, account: serverGroup.account, region: serverGroup.region });
        if (!newServerGroup) {
          $log.debug('server group no longer found, removing:', serverGroup.name, serverGroup.account, serverGroup.region);
          toRemove.push(idx);
        } else {
          if (serverGroup.stringVal !== angular.toJson(newServerGroup)) {
            $log.debug('change detected, updating server group:', serverGroup.name, serverGroup.account, serverGroup.region);
            oldGroup.serverGroups[idx] = newServerGroup;
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
      diffSubgroups(LoadBalancerFilterModel.groups, groups);

      // sort groups in place so Angular doesn't try to update the world
      LoadBalancerFilterModel.groups.sort(function(a, b) {
        if (a.heading < b.heading) {
          return -1;
        }
        if (a.heading > b.heading) {
          return 1;
        }
        return 0;
      });
    }

    function clearFilters() {
      LoadBalancerFilterModel.clearFilters();
      LoadBalancerFilterModel.applyParamsToUrl();
    }

    return {
      updateLoadBalancerGroups: updateLoadBalancerGroups,
      filterLoadBalancersForDisplay: filterLoadBalancersForDisplay,
      sortGroupsByHeading: sortGroupsByHeading,
      clearFilters: clearFilters,
      shouldShowInstance: shouldShowInstance,
    };
  }
);

