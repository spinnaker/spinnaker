'use strict';

var angular = require('angular');
angular.module('deckApp')
  .factory('infrastructureSearch', function(RxService, $q, $http, urlBuilder, settings) {
    return function() {
      var deferred;

      var displayNameLookup = {
        serverGroups: 'serverGroup',
        serverGroupInstances: 'instanceId',
        clusters: 'cluster',
        applications: 'application',
      };

      var categoryNameLookup = {
        serverGroups: 'Server Groups',
        serverGroupInstances: 'Instances',
        clusters: 'Clusters',
        applications: 'Applications',
      };

      var querySubject = new RxService.Subject();

      querySubject
      .throttle(400)
      .distinctUntilChanged()
      .filter(function(input) {
        return angular.isDefined(input) && input.length > 0;
      })
      .flatMap(function(query) {
        return RxService.Observable.fromPromise($http({
          method: 'GET',
          url: settings.oortUrl+'/search?q='+query+'&size='+100
        }));
      })
      .take(1)
      .subscribe(function(result) {
        var tmp = result.data[0].results.reduce(function(categories, entry) {
          var cat = entry.type;
          entry.name = entry[displayNameLookup[entry.type]];
          entry.href = urlBuilder(entry);
          if (angular.isDefined(categories[cat])) {
            categories[cat].push(entry);
          } else {
            categories[cat] = [entry];
          }
          return categories;
        }, {});
        deferred.resolve(Object.keys(tmp).map(function(cat) {
          return {
            category: categoryNameLookup[cat],
            results: tmp[cat],
          };
        }));
      });

      return {
        query: function(query) {
          deferred = $q.defer();
          querySubject.onNext(query);
          return deferred.promise;


        },
      };
    };
  });
