'use strict';

angular.module('spinnaker.stateHelper', [
  'ui.router'
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
  });
