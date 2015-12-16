'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.history.service', [
  require('../cache/deckCacheFactory.js'),
  require('../utils/lodash.js'),
  require('../utils/uuid.service.js'),
])
  .factory('recentHistoryService', function (_, deckCacheFactory, uuidService) {
    const maxItems = 15;

    deckCacheFactory.createCache('history', 'user', {
      version: 3,
      maxAge: 90 * 24 * 60 * 60 * 1000 // 90 days,
    });

    let cache = deckCacheFactory.getCache('history', 'user');

    function getExisting(items, params, keyParams) {
      if (!keyParams) {
        return _.find(items, { params: params });
      }
      return _.find(items, (item) => {
        return keyParams.every((param) => {
          return item.params[param] === params[param];
        });
      });
    }

    function addItem(type, state, params, keyParams) {
      var items = getItems(type).slice(0, maxItems),
          existing = getExisting(items, params, keyParams),
          entry = {
            params: params,
            state: state,
            accessTime: new Date().getTime(),
            extraData: {},
            id: uuidService.generateUuid(),
          };
      if (existing) {
        items.splice(items.indexOf(existing), 1);
      }
      if (items.length === maxItems) {
        items.pop();
      }
      items.push(entry);
      cache.put(type, items);
    }

    function removeItem(type, id) {
      var items = getItems(type),
        existing = _.find(items, (item) => item.id === id);
      if (existing) {
        items.splice(items.indexOf(existing), 1);
        cache.put(type, items);
      }
    }

    function removeLastItem(type) {
      var items = getItems(type);
      if (items.length) {
        items.splice(0, 1);
        cache.put(type, items);
      }
    }

    function getItems(type) {
      var items = cache.get(type);
      return items ? _.sortBy(items, 'accessTime').reverse() : [];
    }

    /**
     * Used to include additional fields needed by display formatters that might not be present in $stateParams,
     * but is resolved in a controller when the view loads
     * See instanceDetails.controller.js for an example
     * @param type
     * @param extraData
     */
    function addExtraDataToLatest(type, extraData) {
      var items = getItems(type);
      if (items.length) {
        items[0].extraData = extraData;
        cache.put(type, items);
      }
    }


    return {
      addItem: addItem,
      getItems: getItems,
      removeItem: removeItem,
      removeLastItem: removeLastItem,
      addExtraDataToLatest: addExtraDataToLatest,
    };
  })
  .run(function($rootScope, recentHistoryService) {
    $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams) {
      if (toState.data && toState.data.history) {
        recentHistoryService.addItem(toState.data.history.type, toState.name, toParams, toState.data.history.keyParams);
      }
    });
  });
