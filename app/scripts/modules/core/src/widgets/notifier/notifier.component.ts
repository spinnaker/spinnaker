import { module } from 'angular';
import { Notifier } from './Notifier';
import { react2angular } from 'react2angular';

import './notifier.component.less';

export const NOTIFIER_COMPONENT = 'spinnaker.core.widgets.notifier.component';

module(NOTIFIER_COMPONENT, []).component('notifier', react2angular(Notifier));
