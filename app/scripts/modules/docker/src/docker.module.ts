import { module } from 'angular';

import { IMAGE_MODULE } from './image/image.module';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const DOCKER_MODULE = 'spinnaker.docker';
module(DOCKER_MODULE, [
  require('./pipeline/stages/bake/dockerBakeStage'),
  require('./pipeline/trigger/dockerTrigger.module'),
  IMAGE_MODULE,
]);
