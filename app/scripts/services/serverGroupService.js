'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('serverGroupService', function (settings, Restangular, $exceptionHandler) {

    var oortEndpoint = Restangular.withConfig(function (RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.oortUrl);
    });

    function getServerGroupEndpoint(application, account, clusterName, serverGroupName) {
      return oortEndpoint.one('applications', application).all('clusters').all(account).all(clusterName).one('aws').one('serverGroups', serverGroupName);
    }

    function getScalingActivities(application, account, clusterName, serverGroupName, region) {
      return getServerGroupEndpoint(application, account, clusterName, serverGroupName).all('scalingActivities').getList({region: region}).then(function(activities) {
        return activities;
      },
      function(error) {
        $exceptionHandler(error, 'error retrieving scaling activities');
        return [];
      });
    }

    return {
      getScalingActivities: getScalingActivities
    };
});

