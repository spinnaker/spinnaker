'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.job.configure.kubernetes.templateSelection.controller', [
  require('../../../../core/job/job.read.service.js'),
  require('../../../../core/utils/lodash.js'),
  require('../CommandBuilder.js'),
])
  .controller('kubernetesJobTemplateSelectionController', function($scope, kubernetesJobCommandBuilder, jobReader, _) {
    var controller = this;

    var noTemplate = { label: 'None', job: null, cluster: null };

    $scope.command.viewState.template = noTemplate;

    $scope.templates = [ noTemplate ];

    var allClusters = _.groupBy(_.filter($scope.application.serverGroups.data, { type: 'kubernetes', category: 'job' }), function(job) {
      return [job.cluster, job.account, job.region].join(':');
    });

    _.forEach(allClusters, function(cluster) {
      var latest = _.sortBy(cluster, 'name').pop();
      $scope.templates.push({
        cluster: latest.cluster,
        account: latest.account,
        region: latest.region,
        jobName: latest.name,
        job: latest
      });
    });

    function applyCommandToScope(command) {
      command.viewState.disableImageSelection = true;
      command.viewState.disableStrategySelection = $scope.command.viewState.disableStrategySelection || false;
      command.viewState.submitButtonLabel = 'Add';
      angular.copy(command, $scope.command);
    }

    function buildEmptyCommand() {
      return kubernetesJobCommandBuilder.buildNewJobCommand($scope.application, {mode: 'createPipeline'}).then(function(command) {
        applyCommandToScope(command);
      });
    }

    function buildCommandFromTemplate(job) {
      return jobReader.getJob($scope.application.name, job.account, job.region, job.name).then(function(details) {
        return kubernetesJobCommandBuilder.buildJobCommandFromExisting($scope.application, details, 'editPipeline').then(function(command) {
          applyCommandToScope(command);
        });
      });
    }

    controller.selectTemplate = function () {
      var selection = $scope.command.viewState.template;
      if (selection && selection.cluster && selection.job) {
        return buildCommandFromTemplate(selection.job);
      } else {
        return buildEmptyCommand();
      }
    };

    controller.useTemplate = function() {
      $scope.state.loaded = false;
      controller.selectTemplate().then(function() {
        $scope.$emit('template-selected');
      });
    };
  });
