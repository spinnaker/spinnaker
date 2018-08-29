import { module } from 'angular';
import 'ngimport';
import { STATE_EVENTS } from './state.events';
import { ReactInjector } from './react.injector';
import { ModalInjector } from './modal.injector';
import { NgReact } from './ngReact';
import './react.uirouter.css';

export const REACT_MODULE = 'spinnaker.core.react';
module(REACT_MODULE, ['bcherny/ngimport', 'ui.router', STATE_EVENTS]).run(function($injector: any) {
  // Make angular services importable and Convert angular components to react
  ReactInjector.initialize($injector);
  ModalInjector.initialize($injector);
  NgReact.initialize($injector);
});
