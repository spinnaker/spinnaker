import { module } from 'angular';

import { STATE_EVENTS } from './state.events';

import './react.uirouter.css';

export const REACT_MODULE = 'spinnaker.core.react';
module(REACT_MODULE, ['ui.router', STATE_EVENTS]);
