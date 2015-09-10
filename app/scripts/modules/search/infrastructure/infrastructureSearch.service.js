'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.infrastructure.search.service', [
  require('../../utils/rx.js'),
  require('../../navigation/urlBuilder.service.js'),
  require('../../applications/applications.read.service.js'),
])
  .factory('infrastructureSearchService', function(RxService, $q, searchService, urlBuilderService, applicationReader) {
    return function() {
      var deferred;

      // TODO: Remove once Oort is indexing all applications
      function searchApplications(query) {
        return applicationReader.listApplications().then((applications) => {
          return applications
            .filter((application) => {
              return application.name.toLowerCase().indexOf(query.toLowerCase()) !== -1;
            })
            .map((application) => {
              return { application: application.name, type: 'applications', provider: 'aws', url: '/applications/' + application.name };
            });
        });
      }

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
          return entry.serverGroup + ' (' + entry.region + ')';
        },
        instances: function(entry) {
          let serverGroup = entry.serverGroup || 'standalone instance';
          return entry.instanceId + ' (' + entry.region + ' - ' + serverGroup + ')';
        },
        clusters: function(entry) {
          return entry.cluster;
        },
        applications: simpleField('application'),
        loadBalancers: function(entry, fromRoute) {
          let name = fromRoute ? entry.name : entry.loadBalancer;
          return name + ' (' + entry.region + ')';
        }
      };

      function augmentSearchResultsWithApplications(searchResults, applications) {
        applications.forEach((application) => {
          if (searchResults.results.every((searchResult) => { return !angular.equals(application, searchResult); })) {
            searchResults.results.push(application);
          }
        });
      }

      var querySubject = new RxService.Subject();

      querySubject
        .flatMapLatest(function(query) {
          if (!query || !angular.isDefined(query) || query.length < 1) {
            return RxService.Observable.just(searchService.getFallbackResults());
          }
          // TODO: use this once Oort searches all applications
          //return RxService.Observable.fromPromise(searchService.search({
          //  q: query,
          //  type: ['applications', 'clusters', 'instances', 'serverGroups', 'loadBalancers'],
          //}));

          return RxService.Observable.fromPromise(
            searchApplications(query).then(function(applications) {
              return searchService.search({
                q: query,
                type: ['applications', 'clusters', 'instances', 'serverGroups', 'loadBalancers'],
              }).then(function(searchResults) {
                augmentSearchResultsWithApplications(searchResults, applications);
                return searchResults;
              });
            })
          );
        })
        .subscribe(function(result) {
          var tmp = result.results.reduce(function(categories, entry) {
            var cat = entry.type;
            entry.name = displayNameFormatter[entry.type](entry);
            entry.href = urlBuilderService.buildFromMetadata(entry);
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
        formatRouteResult: function(type, params) {
          return displayNameFormatter[type](params, true);
        },
      };
    };
  }).name;
