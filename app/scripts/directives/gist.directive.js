'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.gist.directive', [])
  .directive('gist', function ($http) {
    return {
      restrict: 'E',
      template: '<div marked="gist"></div>',
      scope: {
        gistId: '@',
        fileName: '@'
      },
      link: function($scope, $element, $attrs) {
        var url = ['https://api.github.com/gists/', $attrs.gistId].join('');

        function extractFileContent(data, fileName) {
          return data.files[fileName].content;
        }

        $http.get(url)
          .success(function (data) {
            $scope.gist = extractFileContent(data, $attrs.fileName);
          });

      }
    };
  })
;
