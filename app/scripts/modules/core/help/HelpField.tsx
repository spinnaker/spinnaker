import * as React from 'react';
import {angular2react} from 'angular2react';
import {HelpFieldWrapperComponent} from './helpField.component';

import {ReactInjector} from 'core/react';

interface IHelpFieldProps {
  id?: string;
  fallback?: string;
  content?: string;
  placement?: string;
  expand?: boolean;
  label?: string;
}

export let HelpField: React.ComponentClass<IHelpFieldProps> = undefined;
ReactInjector.give(($injector: any) => HelpField = angular2react('helpFieldWrapper', new HelpFieldWrapperComponent(), $injector) as any);
