import { module } from 'angular';

import { Markdown } from './Markdown';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const CORE_PRESENTATION_MARKDOWN = 'core.presentation.markdown';
module(CORE_PRESENTATION_MARKDOWN, []).component(
  'markdown',
  angularComponentFromReact(Markdown, 'markdown', ['message', 'tag', 'trim', 'className', 'options']),
);
