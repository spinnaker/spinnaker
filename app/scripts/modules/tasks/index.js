
angular
  .module('deckApp.tasks', [
    'deckApp.tasks.api',
    'deckApp.tasks.main',
    'deckApp.tasks.detail',
    'deckApp.tasks.monitor',
    'deckApp.tasks.tracker',
    'deckApp.tasks.read.service',
    'deckApp.tasks.write.service'
  ]);
