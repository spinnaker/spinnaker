'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.naming.service', [])
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

    function getClusterName(app, stack, detail) {
      var clusterName = app;
      if (stack) {
        clusterName += '-' + stack;
      }
      if (!stack && detail) {
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

    function parseLoadBalancerName(loadBalancerName) {
      var split = loadBalancerName.split('-'),
        result = {
          application: split[0],
          stack: '',
          freeFormDetails: ''
        };

      if (split.length > 1) {
        result.stack = split[1];
      }
      if (split.length > 2) {
        result.freeFormDetails = split.slice(2, split.length).join('-');
      }
      return result;
    }

    return {
      parseServerGroupName: parseServerGroupName,
      parseLoadBalancerName: parseLoadBalancerName,
      parseSecurityGroupName: parseLoadBalancerName, // seems to be all-purpose, what could go wrong
      getClusterName: getClusterName,
      getSequence: getSequence,
    };
  });
