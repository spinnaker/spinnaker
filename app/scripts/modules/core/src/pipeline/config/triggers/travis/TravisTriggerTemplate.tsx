import React from 'react';

import { BaseBuildTriggerTemplate } from '../baseBuild/BaseBuildTriggerTemplate';
import { BuildServiceType } from '../../../../ci';
import { ITriggerTemplateComponentProps } from '../../../manualExecution/TriggerTemplate';

export class TravisTriggerTemplate extends React.Component<ITriggerTemplateComponentProps> {
  public static formatLabel = BaseBuildTriggerTemplate.formatLabel;

  public render() {
    return <BaseBuildTriggerTemplate {...this.props} buildTriggerType={BuildServiceType.Travis} />;
  }
}
