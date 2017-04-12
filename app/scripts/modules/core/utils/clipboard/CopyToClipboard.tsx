import * as React from 'react';
import { angular2react } from 'angular2react';

import { CopyToClipboardComponent } from './copyToClipboard.component';

interface ICopyToClipboardProps {
  text: string;
  toolTip: string;
  analyticsLabel?: string;
}

export let CopyToClipboard: React.ComponentClass<ICopyToClipboardProps> = undefined;
export const CopyToClipboardInject = ($injector: any) => {
  CopyToClipboard = angular2react<ICopyToClipboardProps>('copyToClipboard', new CopyToClipboardComponent(), $injector);
};
