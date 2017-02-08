'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.transformer.service', [])
  .factory('fastPropertyTransformer', function () {

    function sortRunningPromotionsFirst(promotions) {
      var sorted = _.chain(promotions).transform(function(result, promotion) {
        if(promotion.state === 'Running') {
          result.unshift(promotion);
        } else {
          result.push(promotion);
        }
        return true;
      }, []).value();

      return sorted;
    }

    return {
      sortRunningPromotionsFirst: sortRunningPromotionsFirst,
    };

  });
