import { module } from 'angular';
import 'ngimport';

import { STATE_EVENTS } from './state.events';

import './react.uirouter.css';

export const REACT_MODULE = 'spinnaker.core.react';
module(REACT_MODULE, ['bcherny/ngimport', 'ui.router', STATE_EVENTS]);
