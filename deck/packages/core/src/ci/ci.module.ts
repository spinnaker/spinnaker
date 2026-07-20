'use strict';

import { module } from 'angular';

import './ci.dataSource';
import { CI_STATES } from './ci.states';

export const CI_MODULE = 'spinnaker.ci';
export const name = CI_MODULE;
module(CI_MODULE, [CI_STATES]);
