'use strict';

let angular = require('angular');

require('./execution.less');

module.exports = angular
  .module('spinnaker.core.delivery.group.executionGroup.execution.directive', [
    require('../../filter/executionFilter.service.js'),
    require('../../filter/executionFilter.model.js'),
    require('../../../confirmationModal/confirmationModal.service.js'),
    require('../../../navigation/urlParser.service.js'),
  ])
  .directive('execution', function() {
    return {
      restrict: 'E',
      templateUrl: require('./execution.directive.html'),
      scope: {},
      bindToController: {
        application: '=',
        execution: '=',
      },
      controller: 'ExecutionCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ExecutionCtrl', function ($scope, $location, $stateParams, $state, urlParser,
                                         settings, ExecutionFilterModel, executionService, confirmationModalService) {

    this.pipelinesUrl = [settings.gateUrl, 'pipelines/'].join('/');

    this.showDetails = () => {
      return this.execution.id === $stateParams.executionId &&
        $state.includes('**.execution.**');
    };

    this.isActive = (stageIndex) => {
      return this.showDetails(this.execution.id) && Number($stateParams.stage) === stageIndex;
    };

    this.toggleDetails = (node) => {
      const params = { executionId: node.executionId, stage: node.index};
      if ($state.includes('**.execution', params)) {
        $state.go('^');
      } else {
        if ($state.current.name.indexOf('.executions.execution') !== -1) {
          $state.go('.', params);
        } else {
          $state.go('.execution', params);
        }
      }
    };

    this.getUrl = () => {
      // replace any search text with the execution id
      let [url, queryString] = $location.absUrl().split('?');
      let queryParams = urlParser.parseQueryString(queryString);
      queryParams.q = this.execution.id;
      url += '?';
      let newQueryParts = Object.keys(queryParams).map((param) => param + '=' + queryParams[param]);
      return url + newQueryParts.join('&');
    };

    let updateViewStateDetails = () => {
      this.viewState.activeStageId = Number($stateParams.stage);
      this.viewState.executionId = $stateParams.executionId;
    };

    $scope.$on('$stateChangeSuccess', updateViewStateDetails);

    this.viewState = {
      activeStageId: Number($stateParams.stage),
      executionId: this.execution.id,
      canTriggerPipelineManually: this.pipelineConfig,
      canConfigure: this.pipelineConfig,
      showPipelineName: ExecutionFilterModel.sortFilter.groupBy !== 'name',
    };

    this.deleteExecution = () => {
      confirmationModalService.confirm({
        header: 'Really delete execution?',
        buttonText: 'Delete',
        body: '<p>This will permanently delete the execution history.</p>',
        submitMethod: () => executionService.deleteExecution(this.application, this.execution.id)
      });
    };

    this.cancelExecution = () => {
      confirmationModalService.confirm({
        header: 'Really stop execution of ' + this.execution.name + '?',
        buttonText: 'Stop running ' + this.execution.name,
        destructive: false,
        submitMethod: () => executionService.cancelExecution(this.application, this.execution.id)
      });
    };

    $scope.$on('$destroy', () => {
      if (this.isActive()) {
        this.hideDetails();
      }
    });

  });
