'use strict';


angular.module('deckApp')
  .controller('ServerGroupBasicSettingsCtrl', function($scope, modalWizardService, settings, serverGroupService, searchService, RxService) {

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('location');
      } else {
        modalWizardService.getWizard().markDirty('location');
      }
    });

    function searchImages(q) {
      return new RxService.Observable.fromPromise(
        searchService.search('oort', {
          q: q,
          type: 'namedImages',
          filters: {region: $scope.command.region}
        })
      );
    }

    var imageSearchResultsStream = new RxService.Subject();

    imageSearchResultsStream
      .throttle(250)
      .flatMapLatest(searchImages)
      .subscribe(function (data) {
        $scope.allImageSearchResults = data.results;
      });

    this.searchImages = function(q) {
      if (q) {
        imageSearchResultsStream.onNext(q);
      }
    };

    this.getNamePreview = function() {
      var command = $scope.command;
      if (!command) {
        return '';
      }
      return serverGroupService.getClusterName($scope.applicationName, command.stack, command.freeFormDetails);
    };

  });
