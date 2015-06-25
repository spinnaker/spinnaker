'use strict';

var currentServerGroupTargetHelp = '"Current Server Group" refers to the one currently active.';
var lastServerGroupTargetHelp = '"Last Server Group" is the one prior to the currently active one.';

angular
  .module('spinnaker.stage.constants', [])
  .constant('stageConstants', {
    targetList: [
        {
          label: 'Current Server Group',
          val: 'current_asg',
          description: currentServerGroupTargetHelp
        },
        {
          label: 'Last Server Group',
          val: 'ancestor_asg',
          description: lastServerGroupTargetHelp
        }
      ]
  });
