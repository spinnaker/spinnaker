'use strict';

var angular = require('angular');
angular.module('deckApp')
  .factory('infrastructureSearch', function(RxService, $q, searchService, urlBuilder) {
    return function() {
      var deferred;

      var displayNameLookup = {
        serverGroups: 'serverGroup',
        serverGroupInstances: 'instanceId',
        clusters: 'cluster',
        applications: 'application',
        loadBalancerServerGroups: 'loadBalancer'
      };

      var categoryNameLookup = {
        serverGroups: 'Server Groups',
        serverGroupInstances: 'Instances',
        clusters: 'Clusters',
        applications: 'Applications',
        loadBalancerServerGroups: 'Load Balancers'
      };

      var querySubject = new RxService.Subject();

      querySubject
        .distinctUntilChanged()
        .flatMap(function(query) {
          if (!query || !angular.isDefined(query) || query.length < 1) {
            return RxService.Observable.just({ data: [{ results: [] }] });
          }
          return RxService.Observable.fromPromise(searchService.search({
            q: query,
            type: ['applications', 'clusters', 'serverGroupInstances', 'serverGroups', 'loadBalancerServerGroups'],
          }));
        })
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
