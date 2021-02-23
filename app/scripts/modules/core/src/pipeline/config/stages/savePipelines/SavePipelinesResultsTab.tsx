import { get } from 'lodash';
import React from 'react';

import { PipelineRefList } from './PipleineRefList';
import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';

export class SavePipelinesResultsTab extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'pipelineUpdates';

  public render() {
    const { stage } = this.props;
    const stageContext = get(stage, ['context'], {}) as any;
    return (
      <ExecutionDetailsSection name={this.props.name} current={this.props.current}>
        <PipelineRefList title="Failed Pipeline Updates" pipelineRefs={stageContext.pipelinesFailedToSave || []} />
        <PipelineRefList title="Created Pipelines" pipelineRefs={stageContext.pipelinesCreated || []} />
        <PipelineRefList title="Updated Pipelines" pipelineRefs={stageContext.pipelinesUpdated || []} />
      </ExecutionDetailsSection>
    );
  }
}
