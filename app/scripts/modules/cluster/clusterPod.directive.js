'use strict';

let angular = require('angular');

require('./clusterPod.html');

module.exports = angular.module('spinnaker.cluster.pod', [
  require('./serverGroup.sequence.filter.js'),
  require('../../services/urlbuilder.js'),
])
  .directive('clusterPod', function(urlBuilder) {
    return {
      restrict: 'E',
      scope: {
        grouping: '=',
        sortFilter: '=',
        application: '=',
        parentHeading: '=',
      },
      templateUrl: require('./clusterPod.html'),
      link: function(scope) {
        scope.permalink = urlBuilder.buildFromMetadata(
          {
            type: 'clusters',
            application: scope.application.name,
            cluster: scope.grouping.heading,
            account: scope.parentHeading,
          }
        );
      }
    };
  }).name;
