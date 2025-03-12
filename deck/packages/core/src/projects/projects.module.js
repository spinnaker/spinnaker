'use strict';

import { module } from 'angular';

import './projectSearchResultType';
import { PROJECTS_STATES_CONFIG } from './projects.states';

export const CORE_PROJECTS_PROJECTS_MODULE = 'spinnaker.projects';
export const name = CORE_PROJECTS_PROJECTS_MODULE; // for backwards compatibility
module(CORE_PROJECTS_PROJECTS_MODULE, [PROJECTS_STATES_CONFIG]);
