import { module } from 'angular';

import { SPINNER_WRAPPER } from './Spinner';
import { NOTIFIER_COMPONENT } from './notifier/notifier.component';

export const WIDGETS_MODULE = 'spinnaker.core.widgets';
module(WIDGETS_MODULE, [
  require('./accountNamespaceClusterSelector.component').name,
  require('./accountRegionClusterSelector.component').name,
  require('./scopeClusterSelector.directive').name,
  NOTIFIER_COMPONENT,
  require('./spelText/spelText.decorator').name,
  require('./spelText/spelSelect.component').name,
  require('./spelText/numberInput.component').name,
  require('./actionIcons/actionIcons.component').name,
  SPINNER_WRAPPER,
]);
