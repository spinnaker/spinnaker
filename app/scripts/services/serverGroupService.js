'use strict';


angular.module('deckApp')
  .factory('serverGroupService', function (settings, Restangular, $exceptionHandler, $q, accountService, awsServerGroupService, gceServerGroupService) {

    function getServerGroupEndpoint(application, account, clusterName, serverGroupName) {
      return Restangular.one('applications', application).all('clusters').all(account).all(clusterName).one('serverGroups', serverGroupName);
    }

    function getServerGroup(application, account, region, serverGroupName) {
      return Restangular.one('applications', application).all('serverGroups').all(account).all(region).one(serverGroupName).get();
    }

    function getScalingActivities(application, account, clusterName, serverGroupName, region) {
      return getServerGroupEndpoint(application, account, clusterName, serverGroupName).all('scalingActivities').getList({
        region: region,
        provider: 'aws'
      }).then(function(activities) {
        return activities;
      },
      function(error) {
        $exceptionHandler(error, 'error retrieving scaling activities');
        return [];
      });
    }

    function parseServerGroupName(serverGroupName) {
      var versionPattern = /(v\d{3})/;
      if (!serverGroupName) {
        return {};
      }
      var split = serverGroupName.split('-'),
          isVersioned = versionPattern.test(split[split.length - 1]),
          result = {
            application: split[0],
            stack: '',
            freeFormDetails: ''
          };

      // get rid of version, since we are not returning it
      if (isVersioned) {
        split.pop();
      }

      if (split.length > 1) {
        result.stack = split[1];
      }
      if (split.length > 2) {
        result.freeFormDetails = split.slice(2, split.length).join('-');
      }

      return result;
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

    function getDelegate(provider) {
      return (!provider || provider === 'aws') ? awsServerGroupService : gceServerGroupService;
    }

    function buildNewServerGroupCommand(application, provider) {
      return getDelegate(provider).buildNewServerGroupCommand(application);
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
      return getDelegate(serverGroup.type).buildServerGroupCommandFromExisting(application, serverGroup, mode, parseServerGroupName);
    }

    return {
      getScalingActivities: getScalingActivities,
      parseServerGroupName: parseServerGroupName,
      getClusterName: getClusterName,
      getServerGroup: getServerGroup,
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting
    };
});

