'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperty.form.directive', [])
  .directive('fastPropertyForm', () => {
    return {
      restrict: 'E',
      templateUrl: require('./fastPropertyForm.directive.html')
    };
  })
  .directive('fastPropertyScopeForm', () => {
    return {
      restrict: 'E',
      templateUrl: require('./fastPropertyScopeForm.directive.html')
    };
  })
  .directive('fastPropertyStrategyForm', () => {
    return {
      restrict: 'E',
      templateUrl: require('./fastPropertyStrategyForm.directive.html')
    };
  })
  .directive('fastPropertyAcaTargetForm', () => {
    return {
      restrict: 'E',
      templateUrl: require('./fastPropertyAcaTargetForm.directive.html')
    };
  })
  .directive('fastPropertyNaiveTargetForm', () => {
    return {
      restrict: 'E',
      templateUrl: require('./fastPropertyNaiveTargetForm.directive.html')
    };
  })
  .directive('fastPropertyAcaConfigForm', () => {
    return {
      restrict: 'E',
      templateUrl: require('./fastPropertyAcaConfigForm.directive.html')
    };
  })
  .directive('fastPropertyAcaInstanceStrategyForm', () => {
    return {
      restrict: 'E',
      templateUrl: require('./fastPropertyAcaInstanceStrategyForm.directive.html')
    };
  })
  .directive('fastPropertyReviewForm', () => {
    return {
      restrict: 'E',
      templateUrl: require('./fastPropertyReviewForm.directive.html')
    };
  });
