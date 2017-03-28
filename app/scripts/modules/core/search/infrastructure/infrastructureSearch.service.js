'use strict';

import {Observable, Subject} from 'rxjs';
import {SEARCH_SERVICE} from 'core/search/search.service';
let angular = require('angular');

import {URL_BUILDER_SERVICE} from 'core/navigation/urlBuilder.service';

module.exports = angular.module('spinnaker.infrastructure.search.service', [
  URL_BUILDER_SERVICE,
  SEARCH_SERVICE,
  require('../../cloudProvider/serviceDelegate.service'),
])
  .factory('infrastructureSearchService', function($q, searchService, urlBuilderService, serviceDelegate) {
    return function() {
      var deferred;

      let searchConfig = {
        projects: {
          displayName: 'Projects',
          displayFormatter: function(entry) {
            let applications = entry.config && entry.config.applications ?
            ' (' + entry.config.applications.join(', ') + ')' :
              '';
            let project = entry.name || entry.project;
            return $q.when(project + applications);
          },
          order: 0,
        },
        applications: {
          displayName: 'Applications',
          displayFormatter: simpleField('application'),
          order: 1,
          icon: 'window-maximize',
        },
        clusters: {
          displayName: 'Clusters',
          displayFormatter: simpleField('cluster'),
          order: 2,
          hideIfEmpty: true,
          icon: 'th',
        },
        serverGroups: {
          displayName: 'Server Groups',
          displayFormatter: function(entry) {
            return $q.when(entry.serverGroup + ' (' + entry.region + ')');
          },
          order: 3,
          hideIfEmpty: true,
          icon: 'th-large',
        },
        instances: {
          displayName: 'Instances',
          displayFormatter: function(entry) {
            let serverGroup = entry.serverGroup || 'standalone instance';
            return $q.when(entry.instanceId + ' (' + serverGroup + ' - ' + entry.region + ')');
          },
          order: 4,
          hideIfEmpty: true,
          icon: 'hdd-o'
        },
        loadBalancers: {
          displayName: 'Load Balancers',
          displayFormatter: function(entry, fromRoute) {
            let name = fromRoute ? entry.name : entry.loadBalancer;
            return $q.when(name + ' (' + entry.region + ')');
          },
          order: 5,
          hideIfEmpty: true,
          iconClass: 'icon icon-elb',
        },
        securityGroups: {
          displayName: 'Security Groups',
          displayFormatter: function(entry) {
            return $q.when(entry.name + ' (' + entry.region + ')');
          },
          order: 6,
          hideIfEmpty: true,
          icon: 'exchange',
        }
      };

      function simpleField(field) {
        return function(entry) {
          return $q.when(entry[field]);
        };
      }

      var querySubject = new Subject();

      let initializeCategories = () => {
        let categories = {};
        Object.keys(searchConfig).forEach((searchType) => {
          categories[searchType] = [];
        });
        return categories;
      };

      let formatResult = (category, entry, fromRoute) => {
        var config = searchConfig[category],
            formatter = config.displayFormatter;

        if (serviceDelegate.hasDelegate(entry.provider, 'search.resultFormatter')) {
          let providerFormatter = serviceDelegate.getDelegate(entry.provider, 'search.resultFormatter');
          if (providerFormatter[category]) {
            formatter = providerFormatter[category];
          }
        }
        return formatter(entry, fromRoute);
      };

      querySubject
        .switchMap(function(query) {
          if (!query || !angular.isDefined(query) || query.length < 1) {
            return Observable.of(searchService.getFallbackResults());
          }

          return Observable.fromPromise(searchService.search({
           q: query,
           type: Object.keys(searchConfig),
          }));
        })
        .subscribe(function(result) {
          var tmp = result.results.reduce(function(categories, entry) {
            formatResult(entry.type, entry).then((name) => entry.displayName = name);
            entry.href = urlBuilderService.buildFromMetadata(entry);
            categories[entry.type].push(entry);
            return categories;
          }, initializeCategories());
          deferred.resolve(Object.keys(tmp).map(function(cat) {
            let config = searchConfig[cat];
            return {
              id: cat,
              category: config.displayName,
              icon: config.icon,
              iconClass: config.iconClass,
              order: config.order,
              hideIfEmpty: config.hideIfEmpty,
              results: tmp[cat],
            };
          }));
        });

      return {
        query: function(query) {
          deferred = $q.defer();
          querySubject.next(query);
          return deferred.promise;
        },
        formatRouteResult: function(type, params) {
          return formatResult(type, params, true);
        },
        getCategoryConfig: (category) => searchConfig[category],
      };
    };
  });
