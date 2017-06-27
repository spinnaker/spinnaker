import {module} from 'angular';
import 'ngimport';
import { STATE_EVENTS } from './state.events';
import { REACT_UIROUTER } from './react.uirouter';
import { ReactInjector } from './react.injector';
import { NgReact } from './ngReact';

export const REACT_MODULE = 'spinnaker.core.react';
module(REACT_MODULE, [
  'bcherny/ngimport',
  STATE_EVENTS,
  REACT_UIROUTER,
]).run(function ($injector: any) {
  // Make angular services importable and Convert angular components to react
  ReactInjector.initialize($injector);
  NgReact.initialize($injector);
});
