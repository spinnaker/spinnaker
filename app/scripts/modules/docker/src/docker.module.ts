import { module } from 'angular';

import './pipeline/trigger/DockerTrigger';
import { DOCKER_PIPELINE_STAGES_BAKE_DOCKERBAKESTAGE } from './pipeline/stages/bake/dockerBakeStage';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function (key) {
  templates(key);
});

export const DOCKER_MODULE = 'spinnaker.docker';
module(DOCKER_MODULE, [DOCKER_PIPELINE_STAGES_BAKE_DOCKERBAKESTAGE]);
