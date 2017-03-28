import {module} from 'angular';
import {NETFLIX_CREATE_APPLICATION_MODAL_CONTROLLER} from './netflixCreateApplicationModal.controller';
import {NETFLIX_EDIT_APPLICATION_MODAL_CONTROLLER} from './netflixEditApplicationModal.controller';

export const NETFLIX_APPLICATION_MODULE = 'spinnaker.netflix.application.module';
module(NETFLIX_APPLICATION_MODULE, [
  NETFLIX_CREATE_APPLICATION_MODAL_CONTROLLER, NETFLIX_EDIT_APPLICATION_MODAL_CONTROLLER
]);
