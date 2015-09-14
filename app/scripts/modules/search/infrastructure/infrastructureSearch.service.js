'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.infrastructure.search.service', [
  require('../../utils/rx.js'),
  require('../../navigation/urlBuilder.service.js'),
  require('../../applications/applications.read.service.js'),
  require('../../amazon/vpc/vpc.read.service.js'),
])
  .factory('infrastructureSearchService', function(RxService, $q, searchService, urlBuilderService, applicationReader, vpcReader) {
    return function() {
      var deferred;

      var categoryNameLookup = {
        serverGroups: 'Server Groups',
        instances: 'Instances',
        clusters: 'Clusters',
        applications: 'Applications',
        loadBalancers: 'Load Balancers',
        securityGroups: 'Security Groups'
      };

      function simpleField(field) {
        return function(entry) {
          return $q.when(entry[field]);
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
          return $q.when(entry.serverGroup + ' (' + entry.region + ')');
        },
        instances: function(entry) {
          let serverGroup = entry.serverGroup || 'standalone instance';
          return $q.when(entry.instanceId + ' (' + entry.region + ' - ' + serverGroup + ')');

        },
        clusters: function(entry) {
          return $q.when(entry.cluster);
        },
        applications: simpleField('application'),
        loadBalancers: function(entry, fromRoute) {
          let name = fromRoute ? entry.name : entry.loadBalancer;
          return $q.when(name + ' (' + entry.region + ')');
        },
        securityGroups: function(entry) {
          return vpcReader.getVpcName(entry.vpcId).then(function (vpcName) {
            let region = vpcName ? entry.region + ' - ' + vpcName.toLowerCase() : entry.region;
            return entry.name + ' (' + region + ')';
          });
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

          return RxService.Observable.fromPromise(searchService.search({
           q: query,
           type: ['applications', 'clusters', 'instances', 'serverGroups', 'loadBalancers', 'securityGroups'],
          }));
        })
        .subscribe(function(result) {
          var tmp = result.results.reduce(function(categories, entry) {
            var cat = entry.type;
            displayNameFormatter[entry.type](entry).then((name) => { entry.displayName = name; });
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
