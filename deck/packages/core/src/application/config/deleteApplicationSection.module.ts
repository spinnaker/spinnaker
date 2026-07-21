import { module } from 'angular';

import { DeleteApplicationSection } from './DeleteApplicationSection';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';
export const DELETE_APPLICATION_SECTION = 'spinnaker.core.application.config.delete.directive';
module(DELETE_APPLICATION_SECTION, []).component(
  'deleteApplicationSection',
  angularComponentFromReact(DeleteApplicationSection, 'deleteApplicationSection', ['application']),
);
