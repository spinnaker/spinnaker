'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.instanceList.filter', [])
  .filter('instanceSearch', function () {
    return function (instanceList, query) {
      if (!query) {
        return instanceList;
      }
      if (instanceList && instanceList.length) {
        return instanceList.filter(function (instance) {
          if (query.match('^i-')) {
            if (instance.id.includes(query)) {
              return instance;
            }
          } else {
            return instance;
          }
        });
      }
    };
  });
