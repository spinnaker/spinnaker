import React from 'react';

import { Option } from 'react-select';

import { timestamp } from 'core/utils/timeFormatters';
import { BaseBuildTriggerTemplate } from '../baseBuild/BaseBuildTriggerTemplate';
import { BuildServiceType } from 'core/ci';
import { ITriggerTemplateComponentProps } from '../../../manualExecution/TriggerTemplate';

export class ConcourseTriggerTemplate extends React.Component<ITriggerTemplateComponentProps> {
  private optionRenderer = (build: Option) => {
    return (
      <span style={{ fontSize: '13px' }}>
        <strong>Build {build.number} </strong>
        {build.name} ({timestamp(build.timestamp)})
      </span>
    );
  };

  public static formatLabel = BaseBuildTriggerTemplate.formatLabel;

  public render() {
    return (
      <BaseBuildTriggerTemplate
        {...this.props}
        buildTriggerType={BuildServiceType.Concourse}
        optionRenderer={this.optionRenderer}
      />
    );
  }
}
