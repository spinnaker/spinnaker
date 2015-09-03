'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.cluster.pod', [
  require('./serverGroup.sequence.filter.js'),
  require('../navigation/urlBuilder.service.js'),
  require('../serverGroups/serverGroup.directive.js'),
  require('../utils/stickyHeader/stickyHeader.directive.js'),
])
  .directive('clusterPod', function(urlBuilderService) {
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
        scope.permalink = urlBuilderService.buildFromMetadata(
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
