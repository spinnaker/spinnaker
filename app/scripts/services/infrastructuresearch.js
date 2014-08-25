'use strict';

var angular = require('angular');
angular.module('deckApp')
  .factory('infrastructureSearch', function(RxService, $q, searchService, urlBuilder, settings, _) {
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

      var filters = {
        loadBalancerServerGroups: function (entries) {
          return _.unique(entries, {
            loadBalancer: 'whatever',
            region: 'whatever',
            account: 'whatever'
          });
        }
      };

      var querySubject = new RxService.Subject();

      querySubject
        .distinctUntilChanged()
        .filter(function(input) {
          return input && angular.isDefined(input) && input.length > 0;
        })
        .flatMap(function(query) {
          return RxService.Observable.fromPromise(searchService.search({q: query}));
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
            var results = filters[cat] ? filters[cat](tmp[cat]) : tmp[cat];
            return {
              category: categoryNameLookup[cat],
              results: results
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
