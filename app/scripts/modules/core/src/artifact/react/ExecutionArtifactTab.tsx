import * as React from 'react';
import { get, has, includes } from 'lodash';
import { IExpectedArtifact, IExecution, IExecutionDetailsSectionProps, ExecutionDetailsSection } from 'core';
import { ArtifactIconList } from './ArtifactIconList';

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
    return allArtifacts.filter(a => has(a, 'boundArtifact'));
  }

  private artifactLists() {
    const { stage, execution } = this.props;
    const accumulateArtifacts = (artifacts: string[], field: string): string[] => {
      // fieldValue will be either a string with a single artifact id, or an array of artifact ids
      // In either case, concatenate the value(s) onto the array of artifacts; the one exception
      // is that we don't want to include an empty string in the artifact list, so concatenate
      // an empty array (ie, no-op) if fieldValue is falsey.
      const fieldValue: string | string[] = get(stage, ['context', field], []);
      return artifacts.concat(fieldValue || []);
    };

    const artifactFields = get(this.props, ['config', 'artifactFields'], []);
    const consumedIds = artifactFields.reduce(accumulateArtifacts, []);
    const boundArtifacts = this.extractBoundArtifactsFromExecution(execution);

    const consumedArtifacts = boundArtifacts
      .filter(rea => includes(consumedIds, rea.id))
      .map(rea => rea.boundArtifact)
      .filter(({ name, type }) => name && type);

    const producedArtifacts = get(stage, ['outputs', 'artifacts'], []).slice();

    return { consumedArtifacts, producedArtifacts };
  }

  public render() {
    const { consumedArtifacts, producedArtifacts } = this.artifactLists();
    return (
      <ExecutionDetailsSection name={this.props.name} current={this.props.current}>
        <div className="row execution-artifacts">
          <div className="col-md-6 artifact-list-container">
            <h5>Consumed Artifacts</h5>
            <div>
              <ArtifactIconList artifacts={consumedArtifacts} />
            </div>
          </div>
          <div className="col-md-6 artifact-list-container">
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
