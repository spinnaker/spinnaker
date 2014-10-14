'use strict';


angular.module('deckApp')
  .controller('ServerGroupBasicSettingsCtrl', function($scope, modalWizardService, settings) {

    $scope.select2Params = {
      ajax: {
        url: settings.oortUrl + '/search',
        data: function(term, page) {
          return {
            q: term,
            page: page,
            type: 'namedImages',
            filter: {region: $scope.command.region}
          };
        },
        results: function(data) {
          var results = data[0].results.map(function(result) {
            return {
              text: result.imageName + ' (' + result.imageId + ')',
              id: result.imageName
            };
          });
          return {results: results};
        }
      },
      initSelection: function(elem, callback) {
        var selection = {id: '', text: ''};
        if ($scope.command) {
          var name = $scope.command.amiName || $scope.command.allImageSelection;
          if (name) {
            selection = {id: name, text: name};
          }
        }
        callback(selection);
      },
      minimumInputLength: 2
    };

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('location');
      } else {
        modalWizardService.getWizard().markDirty('location');
      }
    });

  });
