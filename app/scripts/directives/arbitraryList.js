'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('arbitraryList', function (_, $filter) {
    return {
      restrict: 'E',
      templateUrl: 'views/arbitraryList.html',
      scope: {
        source: '=',
        omit: '@',
        includeEmptyValues: '=',
        horizontal: '='
      },
      link: function (scope) {
        if (scope.horizontal) {
          scope.listClass = 'dl-horizontal dl-narrow';
        }

        var source = scope.source;
        var formattedEntries = [],
            children = null;
        _.keys(source).forEach(function (key) {
          var rawEntry = source[key],
            formattedEntry = rawEntry;

          if (key.indexOf('Time') !== -1) {
            formattedEntry = $filter('relativeTime')(rawEntry);
          }

          if (rawEntry && _.isFunction(rawEntry.join)) {
            formattedEntry = rawEntry.join(', ');
          }

          if (rawEntry && _.isPlainObject(rawEntry)) {
            children = [];
            _.forOwn(rawEntry, function(val, key) {
              children.push({key: key, val: val});
            });

          }

          var formattedKey = $filter('robotToHuman')(key);

          formattedKey = formattedKey.replace(scope.omit, '');

          var includeEntry = scope.includeEmptyValues || !!formattedEntry;

          if (includeEntry) {
            formattedEntries.push({label: formattedKey, entry: formattedEntry, children: children});
          }
        });
        formattedEntries = _.sortBy(formattedEntries, 'label');
        scope.formattedEntries = formattedEntries;
      }
    };
  }
);
