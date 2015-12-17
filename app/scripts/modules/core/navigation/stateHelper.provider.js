'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.navigation.stateHelper.provider', [
  require('angular-ui-router')
])
  .provider('stateHelper', function($stateProvider) {

    var setNestedState = function(state, keepOriginalNames){
      var newState = angular.copy(state);
      if (!keepOriginalNames){
        fixStateName(newState);
        fixStateViews(newState);
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

    function fixStateName(state) {
      if (state.parent) {
        state.name = state.parent + '.' + state.name;
      }
    }

    function fixStateViews(state) {
      let views = state.views || {},
          replaced = [];
      Object.keys(views).forEach((key) => {
        var relative = key.match('../');
        if (relative && relative.length) {
          var relativePath = state.parent.split('.').slice(0, -1 - relative.length).join('.') + '.';
          views[key.replace(/(..\/)+/, relativePath)] = views[key];
          replaced.push(key);
        }
      });
      replaced.forEach((key) => delete views[key]);
    }
  });
