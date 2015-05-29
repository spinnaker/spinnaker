'use strict';

angular
  .module('spinnaker.fastProperty.transformer.service', [])
  .factory('fastPropertyTransformer', function() {

    function sortRunningPromotionsFirst(promotions) {
      var sorted = _(promotions).transform(function(result, promotion) {
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
