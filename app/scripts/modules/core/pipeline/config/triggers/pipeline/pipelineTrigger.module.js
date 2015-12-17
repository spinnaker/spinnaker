'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.trigger.pipeline', [
  require('../../services/pipelineConfigService.js'),
  require('../../pipelineConfigProvider.js'),
  require('../../../../application/service/applications.read.service.js'),
  require('../trigger.directive.js'),
  require('../../../../utils/lodash.js'),
])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'Pipeline',
      description: 'Listens to a pipeline execution',
      key: 'pipeline',
      controller: 'pipelineTriggerCtrl',
      controllerAs: 'pipelineTriggerCtrl',
      templateUrl: require('./pipelineTrigger.html'),
      popoverLabelUrl: require('./pipelinePopoverLabel.html')
    });
  })
  .controller('pipelineTriggerCtrl', function ($scope, trigger, pipelineConfigService, applicationReader, _) {

    $scope.trigger = trigger;

    if (!$scope.trigger.application) {
      $scope.trigger.application = $scope.application.name;
    }

    if (!$scope.trigger.status){
      $scope.trigger.status = [];
    }

    $scope.statusOptions = [
      'successful',
      'failed',
      'canceled',
    ];

    function init() {
      if ($scope.trigger.application) {
        pipelineConfigService.getPipelinesForApplication($scope.trigger.application).then(function (pipelines) {
          $scope.pipelines = _.filter(pipelines, function (pipeline) {
            return pipeline.id !== $scope.pipeline.id;
          });
          if (!_.find( pipelines, function(pipeline) { return pipeline.id === $scope.trigger.pipeline; })) {
            $scope.trigger.pipeline = null;
          }
          $scope.viewState.pipelinesLoaded = true;
        });
      }
    }

    $scope.viewState = {
      pipelinesLoaded: false,
      infiniteScroll: {
        numToAdd: 20,
        currentItems: 20,
      },
    };

    this.addMoreItems = function() {
      $scope.viewState.infiniteScroll.currentItems += $scope.viewState.infiniteScroll.numToAdd;
    };

    applicationReader.listApplications().then(function(applications) {
      $scope.applications = _.pluck(applications, 'name').sort();
    });


    $scope.useDefaultParameters = {};
    $scope.userSuppliedParameters = {};

    this.updateParam = function(parameter){
      if($scope.useDefaultParameters[parameter] === true){
        delete $scope.userSuppliedParameters[parameter];
        delete $scope.trigger.parameters[parameter];
      } else if($scope.userSuppliedParameters[parameter]){
        $scope.trigger.pipelineParameters[parameter] = $scope.userSuppliedParameters[parameter];
      }
    };

    init();

    $scope.$watch('trigger.application', init);

  });
