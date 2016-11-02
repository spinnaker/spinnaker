'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular
  .module('cluster.filter.service', [
    require('angular-ui-router'),
    require('./clusterFilter.model'),
    require('./multiselect.model'),
    require('../../utils/waypoints/waypoint.service'),
    require('../../filterModel/filter.model.service'),
  ])
  .factory('clusterFilterService', function (ClusterFilterModel, MultiselectModel, waypointService, $log, $stateParams, $state,
                                             filterModelService) {

    var lastApplication = null;

    /**
     * Filtering logic
     */

    var isFilterable = filterModelService.isFilterable;

    function instanceTypeFilters(serverGroup) {
      if(isFilterable(ClusterFilterModel.sortFilter.instanceType)) {
        var checkedInstanceTypes = filterModelService.getCheckValues(ClusterFilterModel.sortFilter.instanceType);
        return _.includes(checkedInstanceTypes, serverGroup.instanceType);
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

    function addSearchField(serverGroup) {
      if (serverGroup.searchField) {
        return;
      }
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
          _.map(serverGroup.loadBalancers, 'name').join(' '),
          _.map(serverGroup.instances, 'id').join(' ')
        ].join(' ');
      }
    }


    function textFilter(serverGroup) {
      var filter = ClusterFilterModel.sortFilter.filter.toLowerCase();
      if (!filter) {
        return true;
      }
      if (filter.includes('clusters:')) {
        var clusterNames = filter.split('clusters:')[1].replace(/\s/g, '').split(',');
        return clusterNames.includes(serverGroup.cluster);
      }

      if (filter.includes('vpc:')) {
        let [, vpcName] = filter.split('vpc:');
        return serverGroup.vpcName.toLowerCase() === vpcName.toLowerCase();
      }

      if (filter.includes('tag:')) {
        let match = false;
        let [, tag] = filter.split('tag:');
        let tagKey = null;
        let tagValue = null;
        if (tag.includes('=')) {
          [tagKey, tagValue] = tag.split('=');
        }
        _.each(serverGroup.tags || {}, function (val, key) {
          if (tagKey) {
            if (tagKey.toLowerCase() === key.toLowerCase() && val.toLowerCase().includes(tagValue.toLowerCase())) {
              match = true;
            }
          } else if (val.toLowerCase().includes(tag.toLowerCase())) {
            match = true;
          }
        });
        return match;
      }

      if (filter.includes('detail:')) {
        let [, detailName] = filter.split('detail:');
        return serverGroup.detail === detailName.toLowerCase();
      }

      if (filter.includes('cluster:')) {
        let [, clusterName] = filter.split('cluster:');
        return serverGroup.cluster === clusterName;
      } else {
        addSearchField(serverGroup);
        return filter.split(' ').every(function(testWord) {
          return serverGroup.searchField.includes(testWord);
        });
      }
    }

    function filterServerGroupsForDisplay(serverGroups) {
      let result = _.chain(serverGroups)
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

      updateMultiselectInstanceGroups(result);
      updateMultiselectServerGroups(result);
      return result;
    }

    function updateMultiselectInstanceGroups(serverGroups) {
      // removes instance groups, selection of instances that are no longer visible;
      // adds new instance ids if selectAll is enabled for an instance group
      if (ClusterFilterModel.sortFilter.listInstances && ClusterFilterModel.sortFilter.multiselect) {
        let instancesSelected = 0;
        MultiselectModel.instanceGroups.forEach((instanceGroup) => {
          let match = serverGroups.find((serverGroup) => {
            return serverGroup.name === instanceGroup.serverGroup &&
              serverGroup.region === instanceGroup.region &&
              serverGroup.account === instanceGroup.account &&
              serverGroup.type === instanceGroup.cloudProvider;

          });
          if (!match) {
            // leave it in place, just clear the instanceIds so we retain the selectAll selection if it comes
            // back in subsequent filter operations
            instanceGroup.instanceIds.length = 0;
          } else {
            let filteredInstances = match.instances.filter(shouldShowInstance);
            if (instanceGroup.selectAll) {
              instanceGroup.instanceIds = filteredInstances.map((instance) => instance.id);
            } else {
              instanceGroup.instanceIds = filteredInstances
                .filter((instance) => instanceGroup.instanceIds.includes(instance.id))
                .map((instance) => instance.id);
            }
            instancesSelected += instanceGroup.instanceIds.length;
          }
        });
        MultiselectModel.instancesStream.next();
        MultiselectModel.syncNavigation();
      } else {
        MultiselectModel.instanceGroups.length = 0;
      }
    }

    function updateMultiselectServerGroups(serverGroups) {
      if (ClusterFilterModel.sortFilter.multiselect) {
        if (MultiselectModel.serverGroups.length) {
          let remainingKeys = serverGroups.map(MultiselectModel.makeServerGroupKey);
          let toRemove = [];
          MultiselectModel.serverGroups.forEach((group, index) => {
            if (!remainingKeys.includes(group.key)) {
              toRemove.push(index);
            }
          });
          toRemove.reverse().forEach((index) => MultiselectModel.serverGroups.splice(index, 1));
        }
        MultiselectModel.serverGroupsStream.next();
        MultiselectModel.syncNavigation();
      }
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
        if (!checkedAvailabilityZones.includes(instance.availabilityZone)) {
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
        return _.includes(checkedStatus, instance.healthState);
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
    var updateClusterGroups = _.debounce((application) => {
      if (!application) {
        application = lastApplication;
        if (!lastApplication) {
          return null;
        }
      }

      var groups = [];

      var filter = ClusterFilterModel.sortFilter.filter.toLowerCase();
      var serverGroups = filterServerGroupsForDisplay(application.serverGroups.data, filter);

      var accountGroupings = _.groupBy(serverGroups, 'account');

      _.forOwn(accountGroupings, function(accountGroup, accountKey) {
        var categoryGroupings = _.groupBy(accountGroup, 'category'),
          clusterGroups = [];

        _.forOwn(categoryGroupings, function(categoryGroup, categoryKey) {
          var clusterGroupings = _.groupBy(categoryGroup, 'cluster');

          _.forOwn(clusterGroupings, function(clusterGroup, clusterKey) {
            var regionGroupings = _.groupBy(clusterGroup, 'region'),
              regionGroups = [];

            _.forOwn(regionGroupings, function(regionGroup, regionKey) {
              regionGroups.push( {
                heading: regionKey,
                serverGroups: regionGroup,
                category: categoryKey
              } );
            });

            var cluster = getCluster(application, clusterKey, accountKey, categoryKey);
            if (cluster) {
              clusterGroups.push( {
                heading: clusterKey,
                category: categoryKey,
                subgroups: _.sortBy(regionGroups, 'heading'),
                cluster: cluster
              } );
            }
          });

        });

        groups.push( {
          heading: accountKey,
          subgroups: _.sortBy(clusterGroups, ['heading', 'category']),
        } );
      });

      sortGroupsByHeading(groups);
      waypointService.restoreToWaypoint(application.name);
      ClusterFilterModel.addTags();
      lastApplication = application;
      addHealthFlags();
      return groups;
    }, 25);

    function getCluster(application, clusterName, account, category) {
      return (application.clusters || []).find(c => c.account === account && c.name === clusterName && c.category === category);
    }

    function diffSubgroups(oldGroups, newGroups) {
      var groupsToRemove = [];
      oldGroups.forEach(function(oldGroup, idx) {
        let newGroup = (newGroups || []).find(group =>
            group.heading === oldGroup.heading &&
            group.category === oldGroup.category);
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
      if (oldGroup.category !== newGroup.category) {
        return;
      }

      var toRemove = [];
      oldGroup.serverGroups.forEach(function(serverGroup, idx) {
        var newServerGroup = _.find(newGroup.serverGroups, { name: serverGroup.name, account: serverGroup.account, region: serverGroup.region });
        if (!newServerGroup) {
          $log.debug('server group no longer found, removing:', serverGroup.name, serverGroup.account, serverGroup.region, serverGroup.category);
          toRemove.push(idx);
        } else {
          if (serverGroup.stringVal !== newServerGroup.stringVal) {
            $log.debug('change detected, updating server group:', serverGroup.name, serverGroup.account, serverGroup.region, serverGroup.category);
            oldGroup.serverGroups[idx] = newServerGroup;
          }
          if (serverGroup.executions || newServerGroup.executions) {
            serverGroup.executions = newServerGroup.executions;
          }
          if (serverGroup.runningTasks || newServerGroup.runningTasks) {
            serverGroup.runningTasks = newServerGroup.runningTasks;
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


    /**
     * Utility method used to navigate to a specific cluster by setting filters
     */
    function overrideFiltersForUrl(result) {
      if (result.href.includes('/clusters')) {
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
        if (result.category) {
          var category = {};
          category[result.category] = true;
          ClusterFilterModel.sortFilter.category = category;
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
      clearFilters: clearFilters,
      shouldShowInstance: shouldShowInstance,
      overrideFiltersForUrl: overrideFiltersForUrl,
    };
  }
);

