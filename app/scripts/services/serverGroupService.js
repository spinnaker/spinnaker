'use strict';


angular.module('deckApp')
  .factory('serverGroupService', function (settings, Restangular, $exceptionHandler) {

    var oortEndpoint = Restangular.withConfig(function (RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.oortUrl);
    });

    function getServerGroupEndpoint(application, account, clusterName, serverGroupName) {
      return oortEndpoint.one('applications', application).all('clusters').all(account).all(clusterName).one('aws').one('serverGroups', serverGroupName);
    }

    function getServerGroup(application, account, region, serverGroupName) {
      return oortEndpoint.one('applications', application).all('serverGroups').all(account).all(region).one(serverGroupName).get();
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

    function parseServerGroupName(serverGroupName) {
      var asgNameRegex = /(\w+)(-v\d{3})?(-(\w+)?(-v\d{3})?(-(\w+))?)?(-v\d{3})?/;
      var match = asgNameRegex.exec(serverGroupName);
      return {
        application: match[1],
        stack: match[4] || '',
        freeFormDetails: match[7] || ''
      };
    }

    function getClusterName(app, cluster, detail) {
      var clusterName = app;
      if (cluster) {
        clusterName += '-' + cluster;
      }
      if (!cluster && detail) {
        clusterName += '-';
      }
      if (detail) {
        clusterName += '-' + detail;
      }
      return clusterName;
    }

    return {
      getScalingActivities: getScalingActivities,
      parseServerGroupName: parseServerGroupName,
      getClusterName: getClusterName,
      getServerGroup: getServerGroup
    };
});

