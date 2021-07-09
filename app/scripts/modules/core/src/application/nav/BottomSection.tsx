import React from 'react';

import { Application } from '../../index';
import { Overridable } from '../../overrideRegistry';

@Overridable('core.nav.bottom')
export class BottomSection extends React.Component<{ app?: Application }> {
  public render(): React.ReactElement {
    return null;
  }
}
