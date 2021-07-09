import { find, get } from 'lodash';
import React from 'react';

import { AccountTag } from '../../../../account';
import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { StageFailureMessage } from '../../../details';
import { UrlBuilder } from '../../../../navigation';
import { ReactInjector } from '../../../../reactShims';
import { ClusterState } from '../../../../state';

export interface IDeployResult {
  type: string;
  application: string;
  serverGroup: string;
  account: string;
  region: string;
  provider: string;
  project: string;
  href?: string;
}

export interface ICloneServerGroupExecutionDetailsState {
  deployResults: IDeployResult[];
}

export class CloneServerGroupExecutionDetails extends React.Component<
  IExecutionDetailsSectionProps,
  ICloneServerGroupExecutionDetailsState
> {
  public static title = 'cloneServerGroupConfig';

  constructor(props: IExecutionDetailsSectionProps) {
    super(props);
    this.state = { deployResults: [] };
  }

  private addDeployedArtifacts(props: IExecutionDetailsSectionProps): void {
    const context = get(props, 'stage.context', {} as any);
    const tasks = context['kato.tasks'] ?? [];
    if (tasks.length === 0) {
      return;
    }
    const resultObjects: { [key: string]: { [key: string]: string[] } } = tasks[0].resultObjects;
    if (!resultObjects || Object.keys(resultObjects).length === 0) {
      return;
    }

    // Find the result object that contains the passed in key
    let deployResults: IDeployResult[] = [];
    const deployedArtifacts = find(resultObjects, 'serverGroupNames');
    if (deployedArtifacts) {
      const deployedServerGroups = (deployedArtifacts['serverGroupNames'] || []).filter((a) => a.includes(':'));
      deployResults = deployedServerGroups.map((serverGroupNameAndRegion: string) => {
        const [region, serverGroupName] = serverGroupNameAndRegion.split(':');
        const result: IDeployResult = {
          type: 'serverGroups',
          application: context.application,
          serverGroup: serverGroupName,
          account: context.credentials,
          region,
          provider: context.cloudProvider ?? 'aws',
          project: ReactInjector.$stateParams.project,
        };
        result.href = UrlBuilder.buildFromMetadata(result);
        return result;
      });
    }
    this.setState({ deployResults });
  }

  public componentDidMount(): void {
    this.addDeployedArtifacts(this.props);
  }

  public componentWillReceiveProps(nextProps: IExecutionDetailsSectionProps) {
    if (nextProps.stage !== this.props.stage) {
      this.addDeployedArtifacts(nextProps);
    }
  }

  public render() {
    const { deployResults } = this.state;
    const { stage, current, name } = this.props;
    const specifiedCapacity = !stage.context.useSourceCapacity && stage.context.capacity;
    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-9">
            <dl className="dl-narrow dl-horizontal">
              <dt>Account</dt>
              <dd>
                <AccountTag account={stage.context.credentials} />
              </dd>
              <dt>Region</dt>
              <dd>{stage.context.region}</dd>
              <dt>Cluster</dt>
              <dd>{stage.context.targetCluster}</dd>
              <dt>Server Group</dt>
              <dd>{stage.context.source && stage.context.source.serverGroupName}</dd>
              {specifiedCapacity && <dt>Capacity</dt>}
              {specifiedCapacity && (
                <dd>
                  Min: {stage.context.capacity.min} / Desired: {stage.context.capacity.desired} / Max:{' '}
                  {stage.context.capacity.max}
                </dd>
              )}
            </dl>
          </div>
        </div>
        <StageFailureMessage stage={stage} message={stage.failureMessage} />

        {deployResults.length > 0 && (
          <div className="row">
            <div className="col-md-12">
              <div className="well alert alert-info">
                <strong>Deployed: </strong>
                {(deployResults || []).map((result) => (
                  <DeployedServerGroup key={result.href} result={result} />
                ))}
              </div>
            </div>
          </div>
        )}
      </ExecutionDetailsSection>
    );
  }
}

const DeployedServerGroup = (props: { result: IDeployResult }): JSX.Element => {
  const deployClicked = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.stopPropagation();
    ClusterState.filterService.overrideFiltersForUrl(props.result);
  };
  return (
    <a onClick={deployClicked} href={props.result.href}>
      {props.result.serverGroup}
    </a>
  );
};
