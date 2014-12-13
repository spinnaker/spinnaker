'use strict';

angular.module('deckApp')
  .factory('timeBoundaries', function(momentService) {
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

    function isBetween(item, a, b) {
      var ts = momentService(item.startTime);
      return angular.isDefined(a) &&
        ts.isBefore(a.moment()) &&
        (angular.isUndefined(b) || ts.isAfter(b.moment()) || ts.isSame(b.moment()));
    }

    return {
      groupByTimeBoundary: function(items) {
        var ret = {};
        boundaries.reduce(function(curr, next) {
          var filtered = items.filter(function(item) {
            return isBetween(item, curr, next);
          });
          if (filtered.length > 0) {
            ret[curr.name] = filtered;
          }
          return next;
        });
        return ret;
      },
      isContainedWithin: function(item, boundaryName) {
        var boundingIndex = boundaries.reduce(function(curr, next, idx) {
          return next.name === boundaryName ? idx : curr;
        }, -1);
        return isBetween(item, boundaries[boundingIndex], boundaries[boundingIndex + 1]);
      },
    };
  });
