'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.application.listExtractor.service', [
    require('../../utils/lodash')
  ])
  .factory('appListExtractorService', function(_) {

    let popUndefinedValues = (list) => {
      list.forEach( (i) => {
        if (typeof i === 'undefined') { list.pop(); }
      });
    };

    let getRegions = (appList) => {
      return _(appList)
        .map('clusters').flatten()
        .map('serverGroups').flatten()
        .map('region')
        .tap(popUndefinedValues)
        .unique()
        .value();
    };

    let defaultRegionFilter = (serverGroup) => true;

    let getStacks = (appList, regionFilter = defaultRegionFilter) => {
      return _(appList)
        .map('clusters').flatten()
        .map('serverGroups').flatten()
        .filter( regionFilter )
        .map('stack').flatten()
        .tap(popUndefinedValues)
        .unique()
        .value()
        .sort();
    };

    let defaultClusterFilter = (cluster) => true;

    let getClusters = (appList, clusterFilter = defaultClusterFilter) => {
      return _(appList)
        .map('clusters').flatten()
        .filter(clusterFilter)
        .map('name').flatten()
        .tap(popUndefinedValues)
        .unique()
        .value()
        .sort();
    };

    let getAsgs = (appList, clusterFilter = defaultClusterFilter) => {
      return _(appList)
        .map('clusters').flatten()
        .filter(clusterFilter)
        .map('serverGroups').flatten()
        .map('name')
        .tap(popUndefinedValues)
        .unique()
        .value()
        .sort()
        .reverse();
    };

    let defaultServerGroupFilter = (serverGroup) => true;

    let getZones = (appList, clusterFilter = defaultClusterFilter, regionFilter = defaultRegionFilter, serverGroupFilter = defaultServerGroupFilter) => {
      return  _(appList)
        .map('clusters').flatten()
        .filter( clusterFilter )
        .map('serverGroups').flatten()
        .filter( regionFilter )
        .filter( serverGroupFilter )
        .map('instances').flatten()
        .map('availabilityZone').flatten()
        .tap(popUndefinedValues)
        .unique()
        .value();
    };

    let defaultAvailabilityZoneFilter = (instance) => true;

    let getInstances = (appList, clusterFilter = defaultClusterFilter, serverGroupFilter = defaultServerGroupFilter, availabilityZoneFilter = defaultAvailabilityZoneFilter) => {
      return _(appList)
        .map('clusters').flatten()
        .filter(clusterFilter)
        .map('serverGroups').flatten()
        .filter( serverGroupFilter )
        .map('instances').flatten()
        .filter( availabilityZoneFilter )
        .tap(popUndefinedValues)
        .unique()
        .value();
    };

    return {
      getRegions: getRegions,
      getStacks: getStacks,
      getClusters: getClusters,
      getAsgs: getAsgs,
      getZones: getZones,
      getInstances: getInstances,
    };

  })
  .name;
