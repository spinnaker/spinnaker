'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.stage.constants', [])
  .constant('stageConstants', {
    targetList: [
      {
        label: 'Newest Server Group',
        val: 'current_asg_dynamic',
        description: 'Selects the most recently deployed server group when this <b>stage</b> starts.'
      },
      {
        label: 'Previous Server Group',
        val: 'ancestor_asg_dynamic',
        description: 'Selects the second-most recently deployed server group when this <b>stage</b> starts.'
      },
      {
        label: 'Oldest Server Group',
        val: 'oldest_asg_dynamic',
        description: 'Selects the least recently deployed server group when this <b>stage</b> starts.'
      },
      {
        label: '(Deprecated) Current Server Group',
        val: 'current_asg',
        description: 'Selects the most recently deployed server group when the <b>pipeline</b> starts, ignoring any changes ' +
          'to the state of the cluster caused by upstream stages.'
      },
      {
        label: '(Deprecated) Last Server Group',
        val: 'ancestor_asg',
        description: 'Selects the second-most recently deployed server group when the <b>pipeline</b> starts, ignoring any changes ' +
          'to the state of the cluster caused by upstream stages.'
      }
    ]
  })
  .name;
