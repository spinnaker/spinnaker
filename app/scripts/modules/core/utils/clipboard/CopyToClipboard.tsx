import * as React from 'react';
import { angular2react } from 'angular2react';

import { CopyToClipboardComponent } from './copyToClipboard.component';
import { ReactInjector } from 'core/react';

interface ICopyToClipboardProps {
  text: string;
  toolTip: string;
  analyticsLabel?: string;
}

export let CopyToClipboard: React.ComponentClass<ICopyToClipboardProps> = undefined;
ReactInjector.give(($injector: any) => CopyToClipboard = angular2react('copyToClipboard', new CopyToClipboardComponent(), $injector) as any);
