'use strict';

const angular = require('angular');
const Utility = require('../../../../utility').default;

module.exports = angular
  .module('spinnaker.azure.serverGroup.configure.wizard.tags.directive', [])
  .directive('azureTagsSelector', function() {
    return {
      restrict: 'E',
      templateUrl: require('./tagsSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'tagsSelectorCtrl',
      controller: 'TagsSelectorCtrl',
    };
  })
  .controller('TagsSelectorCtrl', [
    '$scope',
    function() {
      this.getTagResult = function() {
        return Utility.checkTags(this.command.instanceTags);
      };
    },
  ]);
