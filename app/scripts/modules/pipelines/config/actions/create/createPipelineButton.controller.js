'use strict';

angular.module('deckApp.pipelines.create')
  .controller('CreatePipelineButtonCtrl', function($scope, $modal) {
    this.createPipeline = function() {
      $modal.open({
        templateUrl: 'scripts/modules/pipelines/config/actions/create/createPipelineModal.html',
        controller: 'CreatePipelineModalCtrl',
        controllerAs: 'createPipelineModalCtrl',
        resolve: {
          application: function() { return $scope.application; }
        }
      })
    }
  });
