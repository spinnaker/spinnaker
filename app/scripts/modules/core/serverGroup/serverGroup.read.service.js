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

    function getScalingActivities(serverGroup) {
      return getServerGroupEndpoint(serverGroup.app, serverGroup.account, serverGroup.cluster, serverGroup.name)
        .all('scalingActivities')
        .withParams({
          region: serverGroup.region,
          provider: serverGroup.cloudProvider
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
