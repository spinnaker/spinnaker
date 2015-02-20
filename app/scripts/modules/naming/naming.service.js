'use strict';

angular.module('deckApp.naming', [])
  .factory('namingService', function() {
    var versionPattern = /(v\d{3})/;

    function parseServerGroupName(serverGroupName) {
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

    function getSequence(serverGroupName) {
      var split = serverGroupName.split('-'),
        isVersioned = versionPattern.test(split[split.length - 1]);

      if (isVersioned) {
        return split.pop();
      }
      return null;
    }

    return {
      parseServerGroupName: parseServerGroupName,
      getClusterName: getClusterName,
      getSequence: getSequence,
    };
  });
