import { module } from 'angular';

import { ConsoleOutputLink } from './ConsoleOutputLink';
import { angularComponentFromReact } from '../../../angular/angularComponentFromReact';

export const CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUTLINK_COMPONENT = 'spinnaker.core.instance.details.console.link';
export const name = CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUTLINK_COMPONENT;

module(name, []).component(
  'consoleOutputLink',
  angularComponentFromReact(ConsoleOutputLink, 'consoleOutputLink', ['instance', 'text', 'usesMultiOutput']),
);
