'use strict';
/* jshint camelcase:false */

let angular = require('angular');

module.exports = angular.module('spinnaker.job.details.kubernetes.controller', [
  require('angular-ui-router'),
  require('../../../core/job/job.read.service.js'),
  require('../../../core/serverGroup/configure/common/runningExecutions.service.js'),
  require('../../../core/utils/lodash.js'),
  require('../../../core/insight/insightFilterState.model.js'),
])
  .controller('kubernetesJobDetailsController', function ($scope, $state, app, job, InsightFilterStateModel,
                                                          jobReader, $uibModal,
                                                          runningExecutionsService, _) {
    let application = app;

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function extractJobSummary() {
      var summary = _.find(application.serverGroups.data, function (toCheck) {
        return toCheck.name === job.name && toCheck.account === job.accountId && toCheck.region === job.region && toCheck.category == 'job';
      });
      if (!summary) {
        application.loadBalancers.data.some(function (loadBalancer) {
          if (loadBalancer.account === job.accountId && loadBalancer.region === job.region) {
            return loadBalancer.jobs.some(function (possibleJob) {
              if (possibleJob.name === job.name) {
                summary = possibleJob;
                return true;
              }
            });
          }
        });
      }
      return summary;
    }

    this.showYaml = function showYaml() {
      $scope.userDataModalTitle = 'Job YAML';
      $scope.userData = $scope.job.yaml;
      $uibModal.open({
        templateUrl: require('../../../core/serverGroup/details/userData.html'),
        controller: 'CloseableModalCtrl',
        scope: $scope
      });
    };

    function retrieveJob() {
      var summary = extractJobSummary();
      return jobReader.getJob(application.name, job.accountId, job.region, job.name).then(function(details) {
        cancelLoader();

        var restangularlessDetails = details.plain();
        angular.extend(restangularlessDetails, summary);

        $scope.job = restangularlessDetails;
        $scope.runningExecutions = function() {
          return runningExecutionsService.filterRunningExecutions($scope.job.executions);
        };
      },
        autoClose
      );
    }

    function autoClose() {
      if ($scope.$$destroyed) {
        return;
      }
      $state.params.allowModalToStayOpen = true;
      $state.go('^', null, {location: 'replace'});
    }

    function cancelLoader() {
      $scope.state.loading = false;
    }

    retrieveJob().then(() => {
      // If the user navigates away from the view before the initial retrieveJob call completes,
      // do not bother subscribing to the refresh
      if (!$scope.$$destroyed) {
        app.serverGroups.onRefresh($scope, retrieveJob);
      }
    });
  });
