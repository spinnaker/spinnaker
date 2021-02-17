import { module } from 'angular';
import { react2angular } from 'react2angular';
import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { ConsoleOutput } from './ConsoleOutput';

export const CORE_CONSOLE_OUTPUT_COMPONENT = 'spinnaker.core.console.output.component';
export const name = CORE_CONSOLE_OUTPUT_COMPONENT; // for backwards compatibility
module(CORE_CONSOLE_OUTPUT_COMPONENT, []).component(
  'consoleOutput',
  react2angular(withErrorBoundary(ConsoleOutput, 'consoleOutput'), ['instance', 'text', 'usesMultiOutput']),
);
