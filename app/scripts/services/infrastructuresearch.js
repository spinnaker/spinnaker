'use strict';

angular.module('deckApp')
  .factory('infrastructureSearch', function(RxService, $q, searchService, urlBuilder) {
    return function() {
      var deferred;

      var categoryNameLookup = {
        serverGroups: 'Server Groups',
        serverGroupInstances: 'Instances',
        clusters: 'Clusters',
        applications: 'Applications',
        loadBalancerServerGroups: 'Load Balancers'
      };

      function simpleField(field) {
        return function(entry) {
          return entry[field];
        };
      }

      var displayNameFormatter = {
        serverGroups: simpleField('serverGroup'),
        serverGroupInstances: function(entry) {
          return entry.instanceId + ' (' + entry.serverGroup + ')';
        },
        clusters: simpleField('cluster'),
        applications: simpleField('application'),
        loadBalancerServerGroups: simpleField('loadBalancer')
      };

      var querySubject = new RxService.Subject();

      querySubject
        .distinctUntilChanged()
        .flatMapLatest(function(query) {
          if (!query || !angular.isDefined(query) || query.length < 1) {
            return RxService.Observable.just(searchService.getFallbackResults());
          }
          return RxService.Observable.fromPromise(searchService.search('oort', {
            q: query,
            type: ['applications', 'clusters', 'serverGroupInstances', 'serverGroups', 'loadBalancerServerGroups'],
          }));
        })
        .subscribe(function(result) {
          var tmp = result.results.reduce(function(categories, entry) {
            var cat = entry.type;
            entry.name = displayNameFormatter[entry.type](entry);
            entry.href = urlBuilder.buildFromMetadata(entry);
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
