import React from 'react';

import { BuildServiceType } from 'core/ci';

import { BaseBuildTriggerTemplate } from '../baseBuild/BaseBuildTriggerTemplate';
import { ITriggerTemplateComponentProps } from '../../../manualExecution/TriggerTemplate';

export class TravisTriggerTemplate extends React.Component<ITriggerTemplateComponentProps> {
  public static formatLabel = BaseBuildTriggerTemplate.formatLabel;

  public render() {
    return <BaseBuildTriggerTemplate {...this.props} buildTriggerType={BuildServiceType.Travis} />;
  }
}
