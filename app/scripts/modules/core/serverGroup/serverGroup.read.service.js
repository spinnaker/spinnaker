'use strict';

import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.serverGroup.read.service', [API_SERVICE])
  .factory('serverGroupReader', function (API, $log) {

    function getServerGroup(application, account, region, serverGroupName) {
      return API.one('applications').one(application).all('serverGroups').all(account).all(region).one(serverGroupName).get();
    }

    function getServerGroupEndpoint(application, account, clusterName, serverGroupName) {
      return API.one('applications').one(application).all('clusters').all(account).all(clusterName).one('serverGroups', serverGroupName);
    }

    function getScalingActivities(application, account, clusterName, serverGroupName, region, provider) {
      return getServerGroupEndpoint(application, account, clusterName, serverGroupName)
        .all('scalingActivities')
        .withParams({
          region: region,
          provider: provider
        })
        .getList()
        .then(function(activities) {
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
