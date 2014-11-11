'use strict';

angular.module('deckApp')
  .controller('TimelineCtrl', function($scope, $element, momentService, $filter) {

    function getTimestampKey() {
      return $scope.timestampGetter.split('.').reduce(function(acc, segment) {
        return acc[segment];
      }, $scope);
    }

    function getItem(idx) {
      return  $filter('orderBy')($filter('filter')($scope[$scope.iterableName], $scope.filterFn),
        function(item) {
          return item[getTimestampKey()];
        }, true)[idx];
    }

    var boundaries = [
      {
        name: 'Today',
        moment: function() {
          return momentService();
        },
      },
      {
        name: 'Yesterday',
        moment: function() {
          return momentService().startOf('day');
        },
      },
      {
        name: 'This Week',
        moment: function() {
          return momentService().startOf('day').subtract(1, 'days');
        },
      },
      {
        name: 'Last Week',
        moment: function() {
          return momentService().startOf('week');
        },
      },
      {
        name: 'Last Month',
        moment: function() {
          return momentService().startOf('month');
        },
      },
      {
        name: 'Prior Years',
        moment: function() {
          return momentService().startOf('year');
        },
      },
    ];

    function boundariesBetween(a, b, getter, boundaries) {
      return boundaries.filter(function(boundary) {
        return (momentService(a[getter]).isAfter(boundary.moment())) &&
          (angular.isDefined(b) && momentService(b[getter]).isBefore(boundary.moment()));
      });
    }

    this.lastBoundaryFollowingIndex = function(idx) {
      var matching = boundariesBetween(getItem(idx),
                               getItem(idx+1),
                               getTimestampKey(),
                               boundaries);
      return matching.length > 0 ? [matching[matching.length -1]] : false;
    };

    this.lastBoundaryAfterIndex = function(idx) {
      var matching = boundaries.filter(function(boundary) {
        return boundary
          .moment()
          .isAfter(momentService(getItem(idx)[getTimestampKey()]));
      });
      return matching.length > 0 ? [matching[matching.length -1]] : false;
    };

    this.getTimestamp = function(item) {
      return item[getTimestampKey()];
    };
  });

