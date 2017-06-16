import { module } from 'angular';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const CANARY_MODULE = 'spinnaker.canary';
module(CANARY_MODULE, [
  require('./acaTask/acaTaskStage.module'),
  require('./canary/canaryStage.module')
]);
