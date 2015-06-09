'use strict';

angular.module('spinnaker.pipelines.trigger.pipeline')
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'Pipeline',
      description: 'Listens to a pipeline execution',
      key: 'pipeline',
      controller: 'pipelineTriggerCtrl',
      controllerAs: 'pipelineTriggerCtrl',
      templateUrl: 'scripts/modules/pipelines/config/triggers/pipeline/pipelineTrigger.html',
      popoverLabelUrl: 'scripts/modules/pipelines/config/triggers/pipeline/pipelinePopoverLabel.html'
    });
  })
  .controller('pipelineTriggerCtrl', function ($scope, trigger, pipelineConfigService) {

    $scope.trigger = trigger;

    if (!$scope.trigger.application) {
      $scope.trigger.application = $scope.application.name;
    }

    if (!$scope.trigger.status){
      $scope.trigger.status = [];
    }

    $scope.statusOptions = [
      'completed',
      'failed',
      'canceled',
    ];

    function init() {
      pipelineConfigService.getPipelinesForApplication($scope.trigger.application).then(function (pipelines) {
        $scope.pipelines = _.filter( pipelines, function(pipeline){ return pipeline.id !== $scope.pipeline.id; } );
        $scope.viewState.pipelinesLoaded = true;
        updatePipelineList();
      });
    }

    $scope.viewState = {
      pipelinesLoaded: false,
    };

    function updatePipelineList() {
    }

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

    $scope.$watch('trigger.pipeline', updatePipelineList);
    $scope.$watch('trigger.application', updatePipelineList);

  });
