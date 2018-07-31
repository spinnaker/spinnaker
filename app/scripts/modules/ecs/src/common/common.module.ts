import { module } from 'angular';

import { ECS_FOOTER_COMPONENT } from './footer.component';

export const COMMON_MODULE = 'spinnaker.ecs.common';
module(COMMON_MODULE, [ECS_FOOTER_COMPONENT]);
