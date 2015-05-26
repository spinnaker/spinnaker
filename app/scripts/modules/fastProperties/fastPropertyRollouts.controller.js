'use strict';


angular
  .module('spinnaker.fastProperties.rollouts.controller', [
    'spinnaker.fastProperty.read.service',
    'spinnaker.fastProperty.write.service'
  ])
  .controller('FastPropertyRolloutController', function ($scope, fastPropertyReader, fastPropertyWriter) {
    var vm = this;

    vm.applicationFilter = '';

    vm.filter = function() {
      if (!_(vm.applicationFilter).isEmpty()) {
        vm.filteredPropmotions = vm.promotions.filter(function(promotion) {
          return promotion.scopes.from.appId.indexOf(vm.applicationFilter) > -1;
        });
      } else {
        vm.filteredPropmotions = vm.promotions;
      }
    };

    vm.continue = function(promotionId) {
      fastPropertyWriter.continuePromotion(promotionId).then(loadPropmotions);
    };

    vm.stop= function(promotionId) {
      window.alert('Stop with: ' + promotionId);
    };

    vm.getLastMessage = function(promotion) {
      return _(promotion.history).last().message;
    };

    function loadPropmotions() {
      fastPropertyReader.loadPromotions().then(function(promotionList) {
        vm.promotions = vm.filteredPropmotions = promotionList;
        vm.filter();
      });
    }

    loadPropmotions();
    return vm;
  });
