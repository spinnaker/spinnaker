'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.runJob.details.controller', [
  require('../../../../utils/lodash.js'),
  require('angular-ui-router'),
  require('../../../../cluster/filter/clusterFilter.service.js'),
  require('../../../../delivery/details/executionDetailsSection.service.js'),
  require('../../../../delivery/details/executionDetailsSectionNav.directive.js'),
  require('../../../../navigation/urlBuilder.service.js'),
])
  .controller('RunJobExecutionDetailsCtrl', function ($scope, _, $stateParams, executionDetailsSectionService, $timeout, urlBuilderService, clusterFilterService) {

    $scope.configSections = ['deploymentConfig', 'taskStatus'];

    function initialize() {

      executionDetailsSectionService.synchronizeSection($scope.configSections);

      $scope.detailsSection = $stateParams.details;

      // When this is called from a stateChangeSuccess event, the stage in the scope is not updated in this digest cycle
      // so we need to wait until the next cycle to update the run artifacts
      $timeout(function () {
        var context = $scope.stage.context || {},
          results = [];

        function addRunArtifacts(key) {
          var runArtifacts = _.find(resultObjects, key);
          if (runArtifacts) {
            _.forEach(runArtifacts[key], function (jobs, region) {
              var result = {
                type: 'jobs',
                application: context.cluster.application,
                job: jobs[0],
                account: context.account,
                region: region,
                provider: context.providerType || context.cloudProvider || 'kubernetes',
                project: $stateParams.project,
              };
              result.href = urlBuilderService.buildFromMetadata(result);
              results.push(result);
            });
          }
        }

        if (context && context['kato.tasks'] && context['kato.tasks'].length) {
          var resultObjects = context['kato.tasks'][0].resultObjects;
          if (resultObjects && resultObjects.length) {
            results = [];
            addRunArtifacts('deployedNamesByLocation');
          }
        }
        $scope.run = results;
        $scope.provider = context.cloudProvider || context.providerType || 'kubernetes';
      });
    }

    this.overrideFiltersForUrl = clusterFilterService.overrideFiltersForUrl;

    this.overrideFiltersForUrl = clusterFilterService.overrideFiltersForUrl;

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
