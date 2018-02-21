'use strict';

import _ from 'lodash';
const angular = require('angular');

import {APPLICATION_READ_SERVICE} from 'core/application/service/application.read.service';
import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';
import {PIPELINE_CONFIG_SERVICE} from 'core/pipeline/config/services/pipelineConfig.service';

module.exports = angular.module('spinnaker.core.pipeline.config.trigger.pipeline', [
  PIPELINE_CONFIG_SERVICE,
  PIPELINE_CONFIG_PROVIDER,
  APPLICATION_READ_SERVICE,
  require('../trigger.directive.js').name,
  require('./pipelineTriggerOptions.directive.js').name,
])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'Pipeline',
      description: 'Listens to a pipeline execution',
      key: 'pipeline',
      controller: 'pipelineTriggerCtrl',
      controllerAs: 'pipelineTriggerCtrl',
      templateUrl: require('./pipelineTrigger.html'),
      manualExecutionHandler: 'pipelineTriggerManualExecutionHandler',
    });
  })
  .factory('pipelineTriggerManualExecutionHandler', function (pipelineConfigService) {
    // must provide two fields:
    //   formatLabel (promise): used to supply the label for selecting a trigger when there are multiple triggers
    //   selectorTemplate: provides the HTML to show extra fields
    return {
      formatLabel: (trigger) => {

        // if this is a re-run, the trigger info will be on the parentExecution; otherwise, check the trigger itself
        // (normalization occurs in the pipelineTriggerOptions component, but that renders after this method is called)
        const application = _.get(trigger, 'parentExecution.application', trigger.application);
        const pipelineConfigId = _.get(trigger, 'parentExecution.pipelineConfigId', trigger.pipeline);

        let loadSuccess = (pipelines) => {
          let pipeline = pipelines.find((config) => config.id === pipelineConfigId);
          return pipeline ? `(Pipeline) ${application}: ${pipeline.name}` : '[pipeline not found]';
        };

        let loadFailure = () => {
          return `[could not load pipelines for '${application}']`;
        };

        return pipelineConfigService.getPipelinesForApplication(application)
          .then(loadSuccess, loadFailure);
      },
      selectorTemplate: require('./selectorTemplate.html'),
    };
  })
  .controller('pipelineTriggerCtrl', function ($scope, trigger, pipelineConfigService, applicationReader) {

    $scope.trigger = trigger;

    if (!$scope.trigger.application) {
      $scope.trigger.application = $scope.application.name;
    }

    if (!$scope.trigger.status) {
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
      $scope.applications = _.map(applications, 'name').sort();
    });


    $scope.useDefaultParameters = {};
    $scope.userSuppliedParameters = {};

    this.updateParam = function(parameter) {
      if ($scope.useDefaultParameters[parameter] === true) {
        delete $scope.userSuppliedParameters[parameter];
        delete $scope.trigger.parameters[parameter];
      } else if($scope.userSuppliedParameters[parameter]) {
        $scope.trigger.pipelineParameters[parameter] = $scope.userSuppliedParameters[parameter];
      }
    };

    init();

    $scope.$watch('trigger.application', init);

  });
