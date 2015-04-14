'use strict';

angular.module('deckApp.pipelines.stage.jenkins.executionDetails.controller', [
  'ui.router',
  'deckApp.executionDetails.section.service',
  'deckApp.executionDetails.section.nav.directive',
])
  .controller('JenkinsExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['jenkinsConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
