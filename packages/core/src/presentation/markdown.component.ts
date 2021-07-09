import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Markdown } from './Markdown';
import { withErrorBoundary } from './SpinErrorBoundary';

export const CORE_PRESENTATION_MARKDOWN = 'core.presentation.markdown';
module(CORE_PRESENTATION_MARKDOWN, []).component(
  'markdown',
  react2angular(withErrorBoundary(Markdown, 'markdown'), ['message', 'tag', 'trim', 'className', 'options']),
);
