import { module } from 'angular';

import { DOCKER_IMAGE_AND_TAG_SELECTOR_COMPONENT } from './dockerImageAndTagSelector.component';

export const IMAGE_MODULE = 'spinnaker.docker.image';
module(IMAGE_MODULE, [
  DOCKER_IMAGE_AND_TAG_SELECTOR_COMPONENT
]);
