'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.help.directive', [
    require('angular-bootstrap'),
    require('angular-marked'),
  ])
  .config(function (markedProvider) {
    markedProvider.setOptions(
      {gfm: true}
    );
  })
  .directive('help', function () {
    return {
      restrict: 'E',
      template: require('./help.directive.html'),
      controller: function($scope, $modal, $log) {

        $scope.showWhatsNew = function() {
          $modal.open({
            template: require('./help.directive.modal.html'),
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
