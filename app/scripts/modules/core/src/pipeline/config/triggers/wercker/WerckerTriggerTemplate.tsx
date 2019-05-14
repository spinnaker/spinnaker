import * as React from 'react';

import { BaseBuildTriggerTemplate } from '../baseBuild/BaseBuildTriggerTemplate';
import { BuildServiceType } from 'core/ci';
import { ITriggerTemplateComponentProps } from 'core/pipeline/manualExecution/TriggerTemplate';

export class WerckerTriggerTemplate extends React.Component<ITriggerTemplateComponentProps> {
  public render() {
    return <BaseBuildTriggerTemplate {...this.props} buildTriggerType={BuildServiceType.Wercker} />;
  }
}
