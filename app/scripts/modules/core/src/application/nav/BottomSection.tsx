import React from 'react';

import { Application } from 'core/index';
import { Overridable } from 'core/overrideRegistry';

@Overridable('core.nav.bottom')
export class BottomSection extends React.Component<{ app?: Application }> {
  public render(): React.ReactElement {
    return null;
  }
}
