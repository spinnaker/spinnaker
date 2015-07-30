'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.infrastructure.search.service', [
  require('utils/rx.js'),
])
  .factory('infrastructureSearchService', function(RxService, $q, searchService, urlBuilder) {
    return function() {
      var deferred;

      var categoryNameLookup = {
        serverGroups: 'Server Groups',
        instances: 'Instances',
        clusters: 'Clusters',
        applications: 'Applications',
        loadBalancers: 'Load Balancers'
      };

      function simpleField(field) {
        return function(entry) {
          return entry[field];
        };
      }

      var sublinks = {
        applications: [
          { name: 'clusters', href: '/clusters' },
          { name: 'pipelines', href: '/../executions' },
          { name: 'tasks', href: '/../tasks' }
        ]
      };

      function applySublinks(entry) {
        if (sublinks[entry.type]) {
          entry.sublinks = sublinks[entry.type];
          entry.href += entry.sublinks[0].href;
        }
      }

      var displayNameFormatter = {
        serverGroups: function(entry) {
          return entry.serverGroup + ' (' + entry.account + ': ' + entry.region + ')';
        },
        instances: function(entry) {
          return entry.instanceId + ' (' + (entry.serverGroup || 'standalone instance') + ')';
        },
        clusters: function(entry) {
          return entry.cluster + ' (' + entry.account + ')';
        },
        applications: simpleField('application'),
        loadBalancers: function(entry) {
          return entry.loadBalancer + ' (' + entry.account + ': ' + entry.region + ')';
        }
      };

      var querySubject = new RxService.Subject();

      querySubject
        .flatMapLatest(function(query) {
          if (!query || !angular.isDefined(query) || query.length < 1) {
            return RxService.Observable.just(searchService.getFallbackResults());
          }
          return RxService.Observable.fromPromise(searchService.search({
            q: query,
            type: ['applications', 'clusters', 'instances', 'serverGroups', 'loadBalancers'],
          }));
        })
        .subscribe(function(result) {
          var tmp = result.results.reduce(function(categories, entry) {
            var cat = entry.type;
            entry.name = displayNameFormatter[entry.type](entry);
            entry.href = urlBuilder.buildFromMetadata(entry);
            applySublinks(entry);
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
  }).name;
