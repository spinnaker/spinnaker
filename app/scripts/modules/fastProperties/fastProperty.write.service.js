'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperty.write.service', [
    require('restangular'),
    require('../utils/lodash.js'),
    require('../authentication/authenticationService.js')
  ])
  .factory('fastPropertyWriter', function (Restangular, authenticationService, _) {

    function upsertFastProperty(fastProperty) {
      var payload = createPromotedPayload(fastProperty);
      return Restangular
        .all('fastproperties')
        .all('promote')
        .post(payload);
    }

    function deleteFastProperty(fastProperty) {
      return Restangular.all('fastproperties')
        .all('delete')
        .remove({
          propId: fastProperty.propertyId,
          cmcTicket: fastProperty.cmcTicket,
          env: fastProperty.env,
        });
    }

    function continuePromotion(promotionId) {
      return Restangular.all('fastproperties')
        .one('promote', promotionId)
        .customPUT(null, null, {pass:true});
    }

    function stopPromotion(promotionId) {
      return Restangular.all('fastproperties')
        .one('promote', promotionId)
        .customPUT(null, null, {pass:false});
    }



    function createPromotedPayload(fastProperty) {
      return _(fastProperty)
        .chain()
        .set('scope', fastProperty.selectedScope)
        .assign(fastProperty.scope, {sourceOfUpdate: 'spinnaker', updatedBy: authenticationService.getAuthenticatedUser().name})
        .omit(['selectedScope', 'impactCount'])
        .value();
    }

    function flattenFastProperty(fastProperty) {
      return _(fastProperty)
        .chain()
        .assign(fastProperty, fastProperty.selectedScope)
        .assign(fastProperty, {sourceOfUpdate: 'spinnaker', updatedBy: authenticationService.getAuthenticatedUser().name})
        .omit('selectedScope')
        .omit('impactCount')
        .value();
    }

    function extractScopeIntoSelectedScope(fastProperty) {
      var scopeProps = _.pick(fastProperty, [
        'env',
        'appId',
        'region',
        'asg',
        'stack',
        'serverId',
        'zone',
        'cluster',
      ]);
      return _.set(fastProperty, 'selectedScope', scopeProps);
    }


    return {
      upsertFastProperty: upsertFastProperty,
      deleteFastProperty: deleteFastProperty,
      flattenFastProperty: flattenFastProperty,
      createPromotedPayload: createPromotedPayload,
      extractScopeIntoSelectedScope: extractScopeIntoSelectedScope,
      continuePromotion: continuePromotion,
      stopPromotion: stopPromotion,
    };
  });
