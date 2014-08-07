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
        var formattedEntries = [];
        _.keys(source).forEach(function (key) {
          var rawEntry = source[key],
            formattedEntry = rawEntry;

          if (key.indexOf('Time') !== -1) {
            formattedEntry = $filter('relativeTime')(rawEntry);
          }

          if (rawEntry && _.isFunction(rawEntry.join)) {
            formattedEntry = rawEntry.join(', ');
          }

          var includeEntry = scope.includeEmptyValues || !!formattedEntry;

          // to upper camel case
          var formattedKey = key.charAt(0).toUpperCase() + key.substr(1);

          // then clear camel case
          formattedKey = formattedKey.replace(/[A-Z]/g, ' $&');

          // then clear snake case
          formattedKey = formattedKey.replace(/_[a-z]/g, function (str) {
            return ' ' + str.charAt(1).toUpperCase() + str.substr(2);
          });

          formattedKey = formattedKey.replace(scope.omit, '');
          formattedKey = formattedKey.replace(/([A-Z])\s([A-Z])\s/g, '$1$2');

          if (includeEntry) {
            formattedEntries.push({label: formattedKey, entry: formattedEntry});
          }
        });
        formattedEntries = _.sortBy(formattedEntries, 'label');
        scope.formattedEntries = formattedEntries;
      }
    };
  }
);
