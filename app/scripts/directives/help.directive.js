'use strict';

angular
  .module('deckApp.help.directive', [
    'ui.bootstrap',
    'hc.marked'
  ])
  .config(function (markedProvider) {
    markedProvider.setOptions(
      {gfm: true}
    );
  })
  .directive('help', function () {
    return {
      restrict: 'E',
      templateUrl: 'scripts/directives/help.directive.html',
      controller: function($scope, $modal, $log) {

        $scope.showWhatsNew = function() {
          $modal.open({
            templateUrl: 'scripts/directives/help.directive.modal.html'
          });
        };

        $scope.status = {
          isopen: false
        };

        $scope.toggled = function(open) {
          $log.log('Dropdown is now: ', open);
        };

        $scope.toggleDropdown = function($event) {
          $event.preventDefault();
          $event.stopPropagation();
          $scope.status.isopen = !$scope.status.isopen;
        };
      }
    };
  });
