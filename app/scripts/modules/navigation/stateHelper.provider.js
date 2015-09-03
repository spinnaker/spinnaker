'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.stateHelper', [
  require('angular-ui-router')
])
  .provider('stateHelper', function($stateProvider) {

    var setNestedState = function(state, keepOriginalNames){
      var newState = angular.copy(state);
      if (!keepOriginalNames){
        fixStateName(newState);
      }
      $stateProvider.state(newState);

      if(newState.children && newState.children.length){
        newState.children.forEach(function(childState){
          childState.parent = newState.name;
          setNestedState(childState, keepOriginalNames);
        });
        delete newState.children;
      }
    };

    this.setNestedState = setNestedState;
    this.$get = angular.noop;

    function fixStateName(state){
      if(state.parent){
        state.name = state.parent + '.' + state.name;
      }
    }
  })
  .name;
