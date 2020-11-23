'use strict';

import { module } from 'angular';

import { CORE_BANNER_CONTAINER_COMPONENT } from './bannerContainer.component';

export const CORE_BANNER_CONTAINER_MODULE = 'spinnaker.core.bannerContainer';
export const name = CORE_BANNER_CONTAINER_MODULE; // for backwards compatibility
module(CORE_BANNER_CONTAINER_MODULE, [CORE_BANNER_CONTAINER_COMPONENT]);
