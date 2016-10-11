'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.promotion.directive', [
    require('../../../modules/core/confirmationModal/confirmationModal.service'),
    require('./fastProperty.write.service.js'),
    require('./fastPropertyScope.service.js')
  ])
  .directive('fastPropertyPromotion', () => {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        promotions: '=',
        filteredPromotions: '=',
        application: '=',
        readOnly: '=',
        promotionPaneOpen: '=',
      },
      templateUrl: require('./fastPropertyPromotion.directive.html'),
      controller: 'fastPropertyPromotionController',
      controllerAs: 'fpPromotion',
    };
  })
  .controller('fastPropertyPromotionController', function(fastPropertyWriter, $timeout, FastPropertyScopeService, confirmationModalService) {
    let vm = this;

    let refreshApp = () => {
      vm.application.refresh(true);
    };

    vm.openRolloutDetailsList = [];

    vm.isRolloutDetailsOpen = function(id) {
      return vm.openRolloutDetailsList.includes(id);
    };

    vm.toggleRolloutDetails = function(promotion) {
      var idIndex = vm.openRolloutDetailsList.indexOf(promotion.id);
      if(vm.isRolloutDetailsOpen(promotion.id)) {
        vm.openRolloutDetailsList.splice(idIndex, 1);
      } else {
        vm.openRolloutDetailsList.push(promotion.id);
      }
    };

    vm.extractScopeFromHistoryMessage = FastPropertyScopeService.extractScopeFromHistoryMessage;

    vm.getLastMessage = function(promotion) {
      if(promotion.history.length > 0) {
        return FastPropertyScopeService.extractScopeFromHistoryMessage(_.chain(promotion.history).last().value().message);
      } else {
        return 'no history';
      }
    };

    vm.continue = function($event, promotion) {
      $event.stopPropagation();
      promotion.isPromoting = true;
      fastPropertyWriter.continuePromotion(promotion.id)
        .then(refreshApp, refreshApp);
    };

    vm.stop = function($event, promotion) {
      $event.stopPropagation();
      promotion.isPromoting = true;
      fastPropertyWriter.stopPromotion(promotion.id)
        .then(refreshApp, refreshApp);
    };

    vm.deletePromotion = function($event, promotion) {
      $event.stopPropagation();
      confirmationModalService.confirm({
        header: 'Really delete promotion?',
        buttonText: 'Delete',
        body: '<p>This will permanently delete the promotion.</p>',
        submitMethod: () => fastPropertyWriter.deletePromotion(promotion.id).then(refreshApp, refreshApp)
      });
    };

  });

