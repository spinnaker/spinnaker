import { find, get, has, isEmpty } from 'lodash';
import { Duration } from 'luxon';
import React from 'react';

import { AccountTag } from '../../../../account/AccountTag';
import { AngularServices } from '../../../../angular/services';
import type { Application } from '../../../../application';
import { CloudProviderRegistry } from '../../../../cloudProvider/CloudProviderRegistry';
import type { IExecutionDetailsSectionProps } from '../common';
import { ExecutionDetailsSection } from '../common';
import { StageFailureMessage } from '../../../details/StageFailureMessage';
import { ViewChangesLink } from '../../../../diffs/ViewChangesLink';
import type { IViewChangesConfig } from '../../../../diffs/ViewChangesLink';
import type { IExecutionDetailsProps, IExecutionStage, IJenkinsInfo } from '../../../../domain';
import { HealthCounts } from '../../../../healthCounts/HealthCounts';
import { HelpContentsRegistry } from '../../../../help';
import { NameUtils } from '../../../../naming/nameUtils';
import { UrlBuilder } from '../../../../navigation/UrlBuilder';
import { Markdown } from '../../../../presentation/Markdown';
import { ViewScalingActivitiesLink } from '../../../../serverGroup/details/scalingActivities/ViewScalingActivitiesLink';
import { ServerGroupReader } from '../../../../serverGroup/serverGroupReader.service';
import { ClusterState } from '../../../../state';

export interface IDeployedServerGroup {
  account: string;
  application: string;
  cloudProvider: string;
  href?: string;
  project: string;
  provider: string;
  region: string;
  serverGroup: string;
  type: string;
}

export interface IDeployWaitingMessages {
  lastCapacityCheck?: any;
  scalingActivitiesTarget?: any;
  showPlatformHealthOverrideMessage: boolean;
  showScalingActivitiesLink: boolean;
  showWaitingMessage: boolean;
  waitingForUpInstances: boolean;
}

export interface IDeployExecutionDetailsState {
  customStuckDeployGuide: string;
  deployed: IDeployedServerGroup[];
  provider: string;
  waitingMessages: IDeployWaitingMessages;
}

export function getClusterName(input: any): string {
  if (!input) {
    return 'n/a';
  }
  return NameUtils.getClusterName(input.application, input.stack, input.freeFormDetails);
}

export function areJarDiffsEmpty(jarDiffs: any): boolean {
  if (isEmpty(jarDiffs)) {
    return true;
  }
  return !Object.keys(jarDiffs).some((key) => Array.isArray(jarDiffs[key]) && jarDiffs[key].length);
}

export function hasDeployChanges(stage: IExecutionStage): boolean {
  const context = stage.context || {};
  return (context.commits && context.commits.length > 0) || !areJarDiffsEmpty(context.jarDiffs);
}

export function getDeployedServerGroups(stage: IExecutionStage, project: string): IDeployedServerGroup[] {
  const context = stage.context || {};
  const katoTasks = context['kato.tasks'];
  const resultObjects = katoTasks && katoTasks.length ? katoTasks[0].resultObjects : null;
  const deployedArtifacts = (resultObjects && find(resultObjects, 'serverGroupNameByRegion')) as any;

  if (!deployedArtifacts) {
    return [];
  }

  return Object.keys(deployedArtifacts.serverGroupNameByRegion).map((region) => {
    const result = {
      type: 'serverGroups',
      application: context.application,
      serverGroup: deployedArtifacts.serverGroupNameByRegion[region],
      account: context.account,
      region,
      provider: context.providerType || context.cloudProvider || 'aws',
      cloudProvider: context.providerType || context.cloudProvider || 'aws',
      project,
    };
    return { ...result, href: UrlBuilder.buildFromMetadata(result) };
  });
}

export function getDeployWaitingMessages(
  stage: IExecutionStage,
  application: Application,
  deployedArtifacts: IDeployedServerGroup[],
): IDeployWaitingMessages {
  const waitingMessages: IDeployWaitingMessages = {
    showWaitingMessage: false,
    waitingForUpInstances: false,
    showScalingActivitiesLink: false,
    showPlatformHealthOverrideMessage: false,
  };

  if (!deployedArtifacts.length) {
    return waitingMessages;
  }

  const deployed = deployedArtifacts[0];
  const activeWaitTask = (stage.tasks || []).find(
    (task) => ['RUNNING', 'TERMINAL'].includes(task.status) && task.name === 'waitForUpInstances',
  );

  if (!activeWaitTask || !stage.context.lastCapacityCheck) {
    return waitingMessages;
  }

  const lastCapacityCheck = {
    ...stage.context.lastCapacityCheck,
    total:
      stage.context.lastCapacityCheck.up +
      stage.context.lastCapacityCheck.down +
      stage.context.lastCapacityCheck.outOfService +
      stage.context.lastCapacityCheck.unknown +
      stage.context.lastCapacityCheck.succeeded +
      stage.context.lastCapacityCheck.failed,
  };
  const waitDurationExceeded = activeWaitTask.runningTimeInMs > Duration.fromObject({ minutes: 5 }).as('milliseconds');

  waitingMessages.showWaitingMessage = true;
  waitingMessages.waitingForUpInstances = activeWaitTask.status === 'RUNNING';
  waitingMessages.lastCapacityCheck = lastCapacityCheck;

  if (CloudProviderRegistry.getValue(stage.context.cloudProvider, 'serverGroup.scalingActivitiesEnabled')) {
    if (waitDurationExceeded && lastCapacityCheck.total < stage.context.capacity.desired) {
      waitingMessages.showScalingActivitiesLink = true;
      waitingMessages.scalingActivitiesTarget = {
        name: deployed.serverGroup,
        app: deployed.application,
        account: deployed.account,
        region: deployed.region,
        cluster: NameUtils.getClusterNameFromServerGroupName(deployed.serverGroup),
        cloudProvider: deployed.cloudProvider,
      };
    }
  }

  if (
    waitDurationExceeded &&
    stage.context.lastCapacityCheck.unknown > 0 &&
    stage.context.lastCapacityCheck.unknown === lastCapacityCheck.total &&
    !stage.context.interestingHealthProviderNames &&
    !get(application, 'attributes.platformHealthOverride', false)
  ) {
    waitingMessages.showPlatformHealthOverrideMessage = true;
  }

  return waitingMessages;
}

export class DeployExecutionDetails extends React.Component<
  IExecutionDetailsSectionProps,
  IDeployExecutionDetailsState
> {
  public static title = 'deploymentConfig';

  constructor(props: IExecutionDetailsSectionProps) {
    super(props);
    this.state = this.buildState(props);
  }

  public componentDidUpdate(prevProps: IExecutionDetailsSectionProps): void {
    if (prevProps.stage !== this.props.stage) {
      this.setState(this.buildState(this.props));
    }
  }

  private buildState(props: IExecutionDetailsSectionProps): IDeployExecutionDetailsState {
    const context = props.stage.context || {};
    const deployed = getDeployedServerGroups(props.stage, AngularServices.$stateParams.project);
    return {
      customStuckDeployGuide: HelpContentsRegistry.getHelpField('execution.stuckDeploy.guide'),
      deployed,
      provider: context.cloudProvider || context.providerType || 'aws',
      waitingMessages: getDeployWaitingMessages(props.stage, props.application, deployed),
    };
  }

  private getConfigHref(): string {
    const applicationName = this.props.application && this.props.application.name;
    return applicationName
      ? AngularServices.$state.href('home.applications.application.config', { application: applicationName })
      : null;
  }

  public render(): React.ReactNode {
    const { current, name, stage } = this.props;
    const context = stage.context || {};
    const { deployed, provider, waitingMessages } = this.state;

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-12">
            <dl className="dl-narrow dl-horizontal">
              <dt>Account</dt>
              <dd>
                <AccountTag account={context.account} />
              </dd>
              <dt>Region</dt>
              {context.availabilityZones &&
                Object.keys(context.availabilityZones).map((region) => (
                  <dd key={region}>
                    {region}
                    <br />({context.availabilityZones[region].join(', ')})
                  </dd>
                ))}
              {context.namespace && <dd>{context.namespace}</dd>}
              {context.region && <dd>{context.region}</dd>}
              <dt>Cluster</dt>
              <dd>{getClusterName(context)}</dd>
              {provider === 'aws' && <dt>VPC</dt>}
              {provider === 'aws' && <dd>{context.subnetType || '[none]'}</dd>}
              <dt>Strategy</dt>
              <dd>{context.strategy || '[none]'}</dd>
              {(context.capacity || context.useSourceCapacity) && <dt>Capacity</dt>}
              {!context.capacity && context.targetSize && <dd>{context.targetSize}</dd>}
              {context.useSourceCapacity && <dd>Current Server Group</dd>}
              {!context.useSourceCapacity && context.capacity && context.capacity.min === context.capacity.max && (
                <dd>{context.capacity.max}</dd>
              )}
              {!context.useSourceCapacity && context.capacity && context.capacity.min !== context.capacity.max && (
                <dd>
                  Min: {context.capacity.min}, Max: {context.capacity.max}, Desired: {context.capacity.desired}
                </dd>
              )}
            </dl>
          </div>
        </div>
        <StageFailureMessage stage={stage} message={stage.failureMessage} />

        {waitingMessages.showWaitingMessage && (
          <div className="well alert-info">
            {waitingMessages.waitingForUpInstances && (
              <p>
                <strong>
                  Waiting for {context.targetDesiredSize} instance{context.targetDesiredSize !== 1 ? 's' : ''} to appear
                  healthy.
                </strong>
                <br />
                {waitingMessages.lastCapacityCheck.total === 0 && <span> (no instances found yet) </span>}
                {waitingMessages.lastCapacityCheck.total !== 0 && (
                  <span>
                    ( current status: <HealthCounts container={waitingMessages.lastCapacityCheck} />)
                  </span>
                )}
              </p>
            )}
            {waitingMessages.waitingForUpInstances && this.state.customStuckDeployGuide && (
              <Markdown message={this.state.customStuckDeployGuide} />
            )}
            {waitingMessages.showScalingActivitiesLink && (
              <div>
                <p>If your instances are not launching, there might be a problem with your configuration.</p>
                <p>
                  <strong>
                    <ViewScalingActivitiesLink serverGroup={waitingMessages.scalingActivitiesTarget} />
                  </strong>{' '}
                  to troubleshoot common configuration issues.
                </p>
              </div>
            )}
            {waitingMessages.showPlatformHealthOverrideMessage && (
              <div>
                <p>
                  By default, Spinnaker does not consider cloud provider health (i.e. whether your instances have
                  launched and are running) as a reliable indicator of instance health.
                </p>
                <p>
                  If your instances do not provide a health indicator known to Spinnaker (e.g. a discovery service or
                  load balancers), you should configure your application to consider only cloud provider health when
                  executing tasks. This option is available under Application Attributes in the{' '}
                  <a href={this.getConfigHref()}>Config tab</a>.
                </p>
              </div>
            )}
          </div>
        )}

        {!!deployed.length && (
          <div className="row">
            <div className="col-md-12">
              <div className="well alert alert-info">
                <strong>Deployed:</strong>{' '}
                {deployed.map((serverGroup) => (
                  <a
                    key={`${serverGroup.region}-${serverGroup.serverGroup}`}
                    onClick={(event) => {
                      event.stopPropagation();
                      ClusterState.filterService.overrideFiltersForUrl(serverGroup);
                    }}
                    href={serverGroup.href}
                  >
                    {serverGroup.serverGroup}
                  </a>
                ))}
              </div>
            </div>
          </div>
        )}
      </ExecutionDetailsSection>
    );
  }
}

export class DeployChangesExecutionDetails extends React.Component<
  IExecutionDetailsSectionProps,
  { jenkins?: IJenkinsInfo }
> {
  private mounted = false;
  private sourceKey: string;
  private sourceRequest = 0;

  constructor(props: IExecutionDetailsSectionProps) {
    super(props);
    this.state = {};
  }

  public componentDidMount(): void {
    this.mounted = true;
    this.loadSourceBuildInfo();
  }

  public componentDidUpdate(): void {
    this.loadSourceBuildInfo();
  }

  public componentWillUnmount(): void {
    this.mounted = false;
  }

  private loadSourceBuildInfo(): void {
    const context = this.props.stage.context || {};
    const region = context.source?.region;
    const serverGroup = context['deploy.server.groups']?.[region]?.[0];
    const source = region && serverGroup ? [context.application, context.account, region, serverGroup] : undefined;
    const sourceKey = JSON.stringify(source);

    if (sourceKey === this.sourceKey) {
      return;
    }

    this.sourceKey = sourceKey;
    const sourceRequest = ++this.sourceRequest;
    if (this.state.jenkins) {
      this.setState({ jenkins: undefined });
    }

    if (source) {
      ServerGroupReader.getServerGroup(source[0], source[1], source[2], source[3])
        .then((serverGroup) => {
          if (this.mounted && sourceRequest === this.sourceRequest && has(serverGroup, 'buildInfo.jenkins')) {
            this.setState({ jenkins: serverGroup.buildInfo.jenkins });
          }
        })
        .catch(() => {});
    }
  }

  public render(): React.ReactNode {
    const context = this.props.stage.context || {};
    const changeConfig: IViewChangesConfig = {
      buildInfo: {
        ...(context.buildInfo || {}),
        ...(this.state.jenkins && { jenkins: this.state.jenkins }),
      },
      commits: context.commits,
      jarDiffs: context.jarDiffs,
    };

    return (
      <ExecutionDetailsSection name={this.props.name} current={this.props.current}>
        <ViewChangesLink viewType="linkOnly" changeConfig={changeConfig} nameItem={this.props.stage} />
      </ExecutionDetailsSection>
    );
  }
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace DeployChangesExecutionDetails {
  export const title = 'changes';
  export const shouldShow = (props: IExecutionDetailsProps) => hasDeployChanges(props.stage);
}
