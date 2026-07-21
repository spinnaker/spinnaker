import { module } from 'angular';

import { NumberList } from './NumberList';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const NUMBER_LIST_COMPONENT = 'spinnaker.core.forms.numberList';
module(NUMBER_LIST_COMPONENT, []).component(
  'numberList',
  angularComponentFromReact(NumberList, 'numberList', ['constraints', 'label', 'model', 'onChange']),
);
