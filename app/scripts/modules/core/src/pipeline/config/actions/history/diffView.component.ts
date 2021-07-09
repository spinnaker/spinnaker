import { module } from 'angular';
import { react2angular } from 'react2angular';

import { DiffView } from './DiffView';
import { withErrorBoundary } from '../../../../presentation/SpinErrorBoundary';

export const DIFF_VIEW_COMPONENT = 'spinnaker.core.pipeline.config.diffView.component';
module(DIFF_VIEW_COMPONENT, []).component('diffView', react2angular(withErrorBoundary(DiffView, 'diffView'), ['diff']));
