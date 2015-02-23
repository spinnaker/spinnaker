'use strict';

angular
  .module('deckApp.gist.directive', [])
  .directive('gist', function ($http) {
    return {
      restrict: 'E',
      template: '<div marked="gist"></div>',
      scope: {
        gistId: '@',
        fileName: '@'
      },
      link: function($scope, $element, $attrs) {
        var t = 'd47428caab832c12c5ef489974d4fbba1d6eed96';
        var url = ['https://api.github.com/gists/', $attrs.gistId, '?access_token=', t].join('');

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
