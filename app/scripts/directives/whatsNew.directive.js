'use strict';

angular
  .module('deckApp.whatsNew.directive', [
    'ui.bootstrap',
    'hc.marked'
  ])
  .config(function (markedProvider) {
    markedProvider.setOptions(
      {gfm: true}
    );
  })
  .directive('whatsNew', function () {
    return {
      restrict: 'E',
      templateUrl: 'scripts/directives/whatsNew.directive.html',
      controller: function($scope, $modal) {
        $scope.showWhatsNew = function() {
          $modal.open({
            templateUrl: 'scripts/directives/whatsNew.directive.modal.html'
          });
        };
      }
    };
  });
