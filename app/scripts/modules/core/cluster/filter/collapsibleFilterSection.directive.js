'use strict';

let angular = require('angular');

module.exports = angular
  .module('cluster.filter.collapse', [])
  .directive('filterSection', function ($timeout) {
    return {
      restrict: 'E',
      transclude: true,
      scope: {
        heading: '@',
        expanded: '@?',
        helpKey: '@?'
      },
      templateUrl: require('./collapsibleFilterSection.html'),
      link:  function (scope, elem) {
        var expanded = (scope.expanded === 'true');
        scope.state = {expanded: expanded};
        scope.getIcon = function () {
          return scope.state.expanded ? 'down' : 'right';
        };

        scope.toggle = function () {
          scope.state.expanded = !scope.state.expanded;
        };

        scope.$on('parent::clearAll', function() {
          $timeout(function() {  // This is needed b/c we don't want to trigger another digest cycle while one is currently in flight.
            elem.find(':checked').trigger('click');
            elem.find('input').val('').trigger('change');
          }, 0, false);
        });
      },

      controller: function($scope) {
        $scope.$on('parent::toggle', function(event, isShow) {
          $scope.state = {expanded: isShow };
        });
      }
    };
  })
  .directive('filterToggleAll', function () {
    return {
      restrict: 'E',
      transclude: true,
      template: [
        '<div>',
          '<div class="btn-group btn-group-xs">',
            '<button class="btn btn-default" ng-click="toggle()">{{buttonName}}</button>',
            '<button class="btn btn-default" ng-click="clearAll()">Clear All</button>',
          '</div>',
          '<div ng-transclude ></div>',
        '</div>'
      ].join(''),
      controller: function ($scope) {
        function toggle() {
          $scope.show = !$scope.show;
          $scope.$broadcast('parent::toggle', $scope.show);
          $scope.buttonName = ($scope.show ? 'Hide All' : 'Show All');
        }

        function clearAll() {
          $scope.$broadcast('parent::clearAll');
        }

        $scope.show = false;
        $scope.buttonName = ($scope.show ? 'Hide All' : 'Show All');
        $scope.toggle = toggle;
        $scope.clearAll = clearAll;
      }
    };
  });
