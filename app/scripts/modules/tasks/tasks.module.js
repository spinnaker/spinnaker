
angular
  .module('spinnaker.tasks', [
    'spinnaker.tasks.api',
    'spinnaker.tasks.main',
    'spinnaker.tasks.detail.controller',
    'spinnaker.tasks.monitor',
    'spinnaker.tasks.tracker',
    'spinnaker.tasks.read.service',
    'spinnaker.tasks.write.service',
    'spinnaker.statusGlyph.directive',
  ]);
