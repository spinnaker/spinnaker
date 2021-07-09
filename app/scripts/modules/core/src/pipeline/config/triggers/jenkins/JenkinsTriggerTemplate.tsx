import React from 'react';

import { BaseBuildTriggerTemplate } from '../baseBuild/BaseBuildTriggerTemplate';
import { BuildServiceType } from '../../../../ci';
import { ITriggerTemplateComponentProps } from '../../../manualExecution/TriggerTemplate';

export class JenkinsTriggerTemplate extends React.Component<ITriggerTemplateComponentProps> {
  public static formatLabel = BaseBuildTriggerTemplate.formatLabel;

  public render() {
    return <BaseBuildTriggerTemplate {...this.props} buildTriggerType={BuildServiceType.Jenkins} />;
  }
}
