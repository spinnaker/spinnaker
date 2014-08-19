'use strict';

var angular = require('angular');
angular.module('deckApp')
  .factory('infrastructureSearch', function(RxService, $q, $http) {
    var querySubject = new RxService.Subject();

    var result = querySubject
    .throttle(400)
    .distinctUntilChanged()
    .filter(function(input) {
      return angular.isDefined(input) && input.length > 0;
    })
    .flatMap(function(query) {
      return RxService.Observable.fromPromise($http({
        method: 'GET',
        url: 'http://oort.prod.netflix.net/search?q='+query+'&size='+100
      }));
    })
    .take(1);

    return {
      query: function(query) {
        querySubject.onNext(query);
        return result;

      },
    };
  });
