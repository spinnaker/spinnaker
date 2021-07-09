import { get, has } from 'lodash';
import React from 'react';

import { ArtifactIconList } from './ArtifactIconList';
import { IExecution, IExpectedArtifact } from '../../domain';
import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../../pipeline';
import { Registry } from '../../registry';

import '../artifactTab.less';

export class ExecutionArtifactTab extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'artifactStatus';

  private extractBoundArtifactsFromExecution(execution: IExecution): IExpectedArtifact[] {
    const triggerArtifacts = get(execution, ['trigger', 'resolvedExpectedArtifacts'], []);
    const stageOutputArtifacts = get(execution, 'stages', []).reduce((out, stage) => {
      const outputArtifacts = get(stage, ['outputs', 'resolvedExpectedArtifacts'], []);
      return out.concat(outputArtifacts);
    }, []);
    const allArtifacts = triggerArtifacts.concat(stageOutputArtifacts);
    return allArtifacts.filter((a) => has(a, 'boundArtifact'));
  }

  private artifactLists() {
    const { stage, execution } = this.props;
    const stageConfig = Registry.pipeline.getStageConfig(stage);
    const stageContext = get(stage, ['context'], {});

    const consumedIds = new Set(
      stageConfig && stageConfig.artifactExtractor ? stageConfig.artifactExtractor(stageContext) : [],
    );

    const boundArtifacts = this.extractBoundArtifactsFromExecution(execution);

    const consumedArtifacts = boundArtifacts
      .filter((rea) => consumedIds.has(rea.id))
      .map((rea) => rea.boundArtifact)
      .filter(({ name, type }) => name && type);

    const producedArtifacts = get(stage, ['outputs', 'artifacts'], []).slice();

    return { consumedArtifacts, producedArtifacts };
  }

  public render() {
    const { consumedArtifacts, producedArtifacts } = this.artifactLists();
    return (
      <ExecutionDetailsSection name={this.props.name} current={this.props.current}>
        <div className="row execution-artifacts">
          <div className="col-sm-6 artifact-list-container">
            <h5>Consumed Artifacts</h5>
            <div>
              <ArtifactIconList artifacts={consumedArtifacts} />
            </div>
          </div>
          <div className="col-sm-6 artifact-list-container">
            <h5>Produced Artifacts</h5>
            <div>
              <ArtifactIconList artifacts={producedArtifacts} />
            </div>
          </div>
        </div>
      </ExecutionDetailsSection>
    );
  }
}
