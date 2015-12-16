'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.promotion.directive', [
    require('../../../modules/core/confirmationModal/confirmationModal.service'),
    require('../../core/utils/lodash.js'),
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
      },
      templateUrl: require('./fastPropertyPromotion.directive.html'),
      controller: 'fastPropertyPromotionController',
      controllerAs: 'fpPromotion',
    };
  })
  .controller('fastPropertyPromotionController', function(_, fastPropertyWriter, $timeout, FastPropertyScopeService, confirmationModalService) {
    let vm = this;

    let refreshApp = () => {
      vm.application.refreshImmediately(true);
    };

    vm.promotionPaneOpen = true;
    vm.openRolloutDetailsList = [];

    vm.isRolloutDetailsOpen = function(id) {
      var idIndex = vm.openRolloutDetailsList.indexOf(id);
      return idIndex > -1;
    };

    vm.toggleRolloutDetails = function(promotion){
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
        return FastPropertyScopeService.extractScopeFromHistoryMessage(_(promotion.history).last().message);
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

    vm.stop= function($event, promotion) {
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

