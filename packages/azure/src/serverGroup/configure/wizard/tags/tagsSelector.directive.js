'use strict';

import { module } from 'angular';

import Utility from '../../../../utility';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_TAGS_TAGSSELECTOR_DIRECTIVE =
  'spinnaker.azure.serverGroup.configure.wizard.tags.directive';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_TAGS_TAGSSELECTOR_DIRECTIVE; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_TAGS_TAGSSELECTOR_DIRECTIVE, [])
  .directive('azureTagsSelector', function () {
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
    function () {
      this.getTagResult = function () {
        return Utility.checkTags(this.command.instanceTags);
      };
    },
  ]);
