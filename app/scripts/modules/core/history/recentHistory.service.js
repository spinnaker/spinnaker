'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.history.service', [
  require('../../caches/deckCacheFactory.js'),
  require('../../utils/lodash.js'),
])
  .factory('recentHistoryService', function (_, deckCacheFactory) {

    const maxItems = 15;

    deckCacheFactory.createCache('history', 'user', {
      maxAge: 90 * 24 * 60 * 60 * 1000 // 90 days,
    });

    let cache = deckCacheFactory.getCache('history', 'user');

    function addItem(type, state, params) {
      var items = _.sortBy(getItems(type), 'accessTime').slice(0, maxItems),
          existing = _.find(items, { state: state, params: params }),
          entry = {
            params: params,
            state: state,
            accessTime: new Date().getTime(),
            extraData: {}
          };
      if (existing) {
        items[items.indexOf(existing)] = entry;
      } else {
        if (items.length === maxItems) {
          items[maxItems - 1] = entry;
        } else {
          items.push(entry);
        }
      }
      cache.put(type, items.reverse());
    }

    function getItems(type) {
      return cache.get(type) || [];
    }

    /**
     * Used to include additional fields needed by display formatters that might not be present in $stateParams,
     * but is resolved in a controller when the view loads
     * See instanceDetails.controller.js for an example
     * @param type
     * @param extraData
     */
    function addExtraDataToLatest(type, extraData) {
      var items = _.sortBy(getItems(type), 'accessTime').reverse();
      items[0].extraData = extraData;
      cache.put(type, items);
    }


    return {
      addItem: addItem,
      getItems: getItems,
      addExtraDataToLatest: addExtraDataToLatest,
    };
  }).name;
