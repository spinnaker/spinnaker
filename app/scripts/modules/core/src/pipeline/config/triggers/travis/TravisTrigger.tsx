import React from 'react';

import { BaseBuildTrigger, IBaseBuildTriggerConfigProps } from '../baseBuild/BaseBuildTrigger';
import { BuildServiceType } from '../../../../ci/igor.service';

export class TravisTrigger extends React.Component<IBaseBuildTriggerConfigProps> {
  public render() {
    return <BaseBuildTrigger {...this.props} buildTriggerType={BuildServiceType.Travis} />;
  }
}
