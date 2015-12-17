'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.timeBoundaries.service', [
  require('../utils/moment.js')
])
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
      if (angular.isUndefined(a)) {
        return false;
      }
      var aIndex = boundaries.indexOf(a);
      var btw = ts.isBefore(a.moment()) &&
        (angular.isUndefined(b) || ts.isAfter(b.moment()) || ts.isSame(b.moment()));
      var exclusive = boundaries.every(function(boundary, idx) {
        var next = boundaries[idx+1];
        return idx >= aIndex || !(ts.isBefore(boundary.moment()) &&
          (ts.isAfter(next.moment()) || ts.isSame(next.moment())));
      });
      return btw && exclusive;
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
