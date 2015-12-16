'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.serverGroup.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
  ])
  .factory('serverGroupReader', function (Restangular, $log) {

    function getServerGroup(application, account, region, serverGroupName) {
      return Restangular.one('applications', application).all('serverGroups').all(account).all(region).one(serverGroupName).get();
    }

    function getServerGroupEndpoint(application, account, clusterName, serverGroupName) {
      return Restangular.one('applications', application).all('clusters').all(account).all(clusterName).one('serverGroups', serverGroupName);
    }

    function getScalingActivities(application, account, clusterName, serverGroupName, region, provider) {
      return getServerGroupEndpoint(application, account, clusterName, serverGroupName).all('scalingActivities').getList({
        region: region,
        provider: provider
      }).then(function(activities) {
          return activities;
        },
        function(error) {
          $log.error(error, 'error retrieving scaling activities');
          return [];
        });
    }

    return {
      getServerGroup: getServerGroup,
      getScalingActivities: getScalingActivities,
    };
  });
