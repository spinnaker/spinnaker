import React from 'react';

import type { IBaseBuildTriggerConfigProps } from '../baseBuild/BaseBuildTrigger';
import { BaseBuildTrigger } from '../baseBuild/BaseBuildTrigger';
import { BuildServiceType } from '../../../../ci/igor.service';

export class JenkinsTrigger extends React.Component<IBaseBuildTriggerConfigProps> {
  public render() {
    return <BaseBuildTrigger {...this.props} buildTriggerType={BuildServiceType.Jenkins} />;
  }
}
