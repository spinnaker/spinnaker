import { module } from 'angular';

import { DiffView } from './DiffView';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const DIFF_VIEW_COMPONENT = 'spinnaker.core.pipeline.config.diffView.component';
module(DIFF_VIEW_COMPONENT, []).component('diffView', angularComponentFromReact(DiffView, 'diffView', ['diff']));
