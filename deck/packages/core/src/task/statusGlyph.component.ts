import { module } from 'angular';

import { StatusGlyph } from './StatusGlyph';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const STATUS_GLYPH_COMPONENT = 'spinnaker.core.task.statusGlyph.component';
module(STATUS_GLYPH_COMPONENT, []).component(
  'statusGlyph',
  angularComponentFromReact(StatusGlyph, 'statusGlyph', ['item']),
);
