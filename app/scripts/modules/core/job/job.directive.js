'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.job.job.directive', [
  require('../cluster/filter/clusterFilter.service'),
  require('../cluster/filter/clusterFilter.model'),
  require('../cluster/filter/multiselect.model'),
  require('../instance/instances.directive'),
  require('../instance/instanceList.directive'),
  require('./job.transformer'),
])
  .directive('job', function ($rootScope, $timeout, $filter, _, clusterFilterService,
                              MultiselectModel, ClusterFilterModel, jobTransformer) {
    return {
      restrict: 'E',
      templateUrl: require('./job.directive.html'),
      scope: {
        cluster: '=',
        job: '=',
        application: '=',
        parentHeading: '=',
        hasLoadBalancers: '=',
        hasDiscovery: '=',
      },
      link: function (scope) {

        let lastStringVal = null;
        scope.sortFilter = ClusterFilterModel.sortFilter;
        scope.multiselectModel = MultiselectModel;

        scope.$state = $rootScope.$state;

        function setViewModel() {
          var filteredInstances = scope.job.instances.filter(clusterFilterService.shouldShowInstance);

          var job = scope.job;

          let viewModel = {
            waypoint: [job.account, job.region, job.name].join(':'),
            job: job,
            jobSequence: $filter('serverGroupSequence')(job.name),
            jenkins: null,
            hasBuildInfo: !!job.buildInfo,
            instances: filteredInstances,
          };

          let modelStringVal = JSON.stringify(viewModel, jobTransformer.jsonReplacer);

          if (lastStringVal !== modelStringVal) {
            scope.viewModel = viewModel;
            lastStringVal = modelStringVal;
          }

          viewModel.job.runningTasks = job.runningTasks;
          viewModel.job.executions = job.executions;
        }

        scope.loadDetails = function(event) {
          $timeout(() => {
            if (event.isDefaultPrevented() || (event.originalEvent && (event.originalEvent.defaultPrevented || event.originalEvent.target.href))) {
              return;
            }
            MultiselectModel.toggleServerGroup(scope.job);
            event.preventDefault();
          });
        };

        setViewModel();

        scope.headerIsSticky = function() {
          if (!scope.sortFilter.showAllInstances) {
            return false;
          }
          if (scope.sortFilter.listInstances) {
            return scope.viewModel.instances.length > 1;
          }
          return scope.viewModel.instances.length > 20;
        };

        scope.$watch('sortFilter', setViewModel, true);
      }
    };
});
