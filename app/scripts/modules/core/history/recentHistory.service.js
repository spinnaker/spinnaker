'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.history.service', [
  require('../../caches/deckCacheFactory.js'),
  require('../../utils/lodash.js'),
])
  .factory('recentHistoryService', function (_, deckCacheFactory) {
    const maxItems = 15;

    deckCacheFactory.createCache('history', 'user', {
      version: 2, // that was quick
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
            extraData: {}
          };
      if (existing) {
        items.splice(items.indexOf(existing), 1);
      }
      if (items.length === maxItems) {
        items.pop();
      }
      items.push(entry);
      cache.put(type, items.reverse());
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
      items[0].extraData = extraData;
      cache.put(type, items);
    }


    return {
      addItem: addItem,
      getItems: getItems,
      addExtraDataToLatest: addExtraDataToLatest,
    };
  }).name;
