import {module} from 'angular';

import {SPINNER_WRAPPER} from './Spinner';

export const WIDGETS_MODULE = 'spinnaker.core.widgets';
module(WIDGETS_MODULE, [
  require('./accountNamespaceClusterSelector.component'),
  require('./accountRegionClusterSelector.component'),
  require('./scopeClusterSelector.directive'),
  require('./notifier/notifier.component.js'),
  require('./spelText/spelText.decorator'),
  require('./spelText/spelSelect.component'),
  require('./spelText/numberInput.component'),
  require('./actionIcons/actionIcons.component'),
  SPINNER_WRAPPER,
]);
