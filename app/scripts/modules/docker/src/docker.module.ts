import { module } from 'angular';

import './pipeline/trigger/DockerTrigger';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const DOCKER_MODULE = 'spinnaker.docker';
module(DOCKER_MODULE, [require('./pipeline/stages/bake/dockerBakeStage').name]);
