import { UISref } from '@uirouter/react';
import { isEmpty } from 'lodash';
import React from 'react';

import { buildIpRulesModel, buildSecurityGroupRulesModel, IPRangeRules, VpcReader } from '@spinnaker/amazon';
import type {
  Application,
  IRouterInjectedProps,
  ISecurityGroup,
  ISecurityGroupDetail,
  SecurityGroupReader,
} from '@spinnaker/core';
import {
  AccountTag,
  CloudProviderLogo,
  CollapsibleSection,
  DeckRuntimeContext,
  FirewallLabels,
  RecentHistoryService,
  Spinner,
  withRouter,
} from '@spinnaker/core';

interface IEcsResolvedSecurityGroup {
  accountId: string;
  name: string;
  provider?: string;
  region: string;
  vpcId?: string;
}

interface IEcsSecurityGroupDetailsProps {
  app: Application;
  autoClose?: () => void;
  resolvedSecurityGroup: IEcsResolvedSecurityGroup;
  securityGroupReader?: SecurityGroupReader;
  vpcReader?: Pick<typeof VpcReader, 'getVpcName'>;
}

interface IEcsSecurityGroupDetailsState {
  loading: boolean;
  notFound?: boolean;
  securityGroup?: ISecurityGroupDetail & ISecurityGroup & Record<string, any>;
}

export class EcsSecurityGroupDetailsComponent extends React.Component<
  IEcsSecurityGroupDetailsProps & IRouterInjectedProps,
  IEcsSecurityGroupDetailsState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  public state: IEcsSecurityGroupDetailsState = { loading: true };

  private isUnmounted = false;
  private loadRequestId = 0;
  private unsubscribeFromRefresh: () => void;

  public componentDidMount(): void {
    const dataSource = this.props.app.getDataSource?.('securityGroups');
    if (!dataSource) {
      this.loadSecurityGroup();
      return;
    }

    dataSource.ready().then(() => {
      if (this.isUnmounted) {
        return;
      }
      this.loadSecurityGroup();
      this.unsubscribeFromRefresh = dataSource.onRefresh(null, this.loadSecurityGroup);
    });
  }

  public componentDidUpdate(prevProps: IEcsSecurityGroupDetailsProps): void {
    if (this.getCoordinatesKey(prevProps) !== this.getCoordinatesKey(this.props)) {
      this.loadSecurityGroup();
    }
  }

  public componentWillUnmount(): void {
    this.isUnmounted = true;
    this.unsubscribeFromRefresh?.();
  }

  private getCoordinatesKey(props: IEcsSecurityGroupDetailsProps): string {
    const { accountId, name, region, vpcId } = props.resolvedSecurityGroup;
    return [accountId, region, vpcId || '', name].join(':');
  }

  private getSecurityGroupReader(): SecurityGroupReader {
    return this.props.securityGroupReader || this.context.services.securityGroupReader;
  }

  private getVpcReader(): Pick<typeof VpcReader, 'getVpcName'> {
    return this.props.vpcReader || VpcReader;
  }

  private showNotFound = (): void => {
    if (this.isUnmounted) {
      return;
    }
    if (this.props.autoClose) {
      this.props.autoClose();
      return;
    }
    if (this.props.app.isStandalone) {
      RecentHistoryService.removeLastItem('securityGroups');
      this.setState({ loading: false, notFound: true, securityGroup: undefined });
      return;
    }
    this.props.stateService.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
  };

  private loadSecurityGroup = (): void => {
    const { app, resolvedSecurityGroup } = this.props;
    const requestId = ++this.loadRequestId;
    this.setState({ loading: true, notFound: false, securityGroup: undefined });

    this.getSecurityGroupReader()
      .getSecurityGroupDetails(
        app,
        resolvedSecurityGroup.accountId,
        'ecs',
        resolvedSecurityGroup.region,
        resolvedSecurityGroup.vpcId || '',
        resolvedSecurityGroup.name,
      )
      .then((details: ISecurityGroupDetail & ISecurityGroup & Record<string, any>) => {
        if (this.isUnmounted || requestId !== this.loadRequestId) {
          return;
        }
        if (!details || isEmpty(details)) {
          this.showNotFound();
          return;
        }

        Promise.resolve(this.getVpcReader().getVpcName(details.vpcId)).then(
          (vpcName) => {
            if (this.isUnmounted || requestId !== this.loadRequestId) {
              return;
            }
            const applicationSecurityGroup = this.getSecurityGroupReader().getApplicationSecurityGroup?.(
              app,
              resolvedSecurityGroup.accountId,
              resolvedSecurityGroup.region,
              resolvedSecurityGroup.name,
            );
            this.setState({
              loading: false,
              securityGroup: {
                ...(applicationSecurityGroup || {}),
                ...details,
                accountId: details.accountId || resolvedSecurityGroup.accountId,
                name: details.name || resolvedSecurityGroup.name,
                region: details.region || resolvedSecurityGroup.region,
                vpcId: details.vpcId || resolvedSecurityGroup.vpcId,
                vpcName,
              },
            });
          },
          () => {
            if (!this.isUnmounted && requestId === this.loadRequestId) {
              this.showNotFound();
            }
          },
        );
      })
      .catch(() => {
        if (!this.isUnmounted && requestId === this.loadRequestId) {
          this.showNotFound();
        }
      });
  };

  private closeDetails = (): void => {
    this.props.stateService.go('^');
  };

  public render(): JSX.Element {
    const { app, resolvedSecurityGroup } = this.props;
    const { loading, notFound, securityGroup } = this.state;

    if (notFound) {
      return (
        <div className="text-center">
          <h3>
            Could not find {FirewallLabels.get('firewall')} {resolvedSecurityGroup.name}.
          </h3>
          <UISref to="home.infrastructure">
            <a>Back to search results</a>
          </UISref>
        </div>
      );
    }

    const ipRules = securityGroup ? buildIpRulesModel(securityGroup) : [];
    const securityGroupRules = securityGroup ? buildSecurityGroupRulesModel(securityGroup) : [];

    return (
      <div className="details-panel">
        <div className="header">
          {!app.isStandalone && (
            <div className="close-button">
              <a className="btn btn-link" onClick={this.closeDetails}>
                <span className="glyphicon glyphicon-remove" />
              </a>
            </div>
          )}
          {loading && (
            <div className="horizontal center middle">
              <Spinner size="small" />
            </div>
          )}
          {!loading && securityGroup && (
            <div className="header-text horizontal middle">
              <CloudProviderLogo provider="ecs" height="36px" width="36px" />
              <h3>{securityGroup.name || '(not found)'}</h3>
            </div>
          )}
        </div>
        {!loading && securityGroup && (
          <div className="content">
            <CollapsibleSection heading={`${FirewallLabels.get('Firewall')} Details`} defaultExpanded={true}>
              <dl className="dl-horizontal dl-medium">
                <dt>ID</dt>
                <dd>{securityGroup.id}</dd>
                <dt>Account</dt>
                <dd>
                  <AccountTag account={securityGroup.accountName || securityGroup.accountId} />
                </dd>
                <dt>Region</dt>
                <dd>{securityGroup.region}</dd>
                <dt>VPC</dt>
                <dd>{securityGroup.vpcName || securityGroup.vpcId}</dd>
                <dt>Description</dt>
                <dd>{securityGroup.description}</dd>
              </dl>
            </CollapsibleSection>
            <IPRangeRules ipRules={ipRules} />
            {!ipRules.length && <div data-test-id="ecs-ip-rules-empty">None</div>}
            <CollapsibleSection
              heading={`${FirewallLabels.get('Firewall')} Rules (${securityGroupRules.length})`}
              defaultExpanded={securityGroupRules.length > 0}
            >
              {!securityGroupRules.length && <div data-test-id="ecs-security-group-rules-empty">None</div>}
              {securityGroupRules.map((rule) => (
                <dl className="dl-horizontal dl-medium" key={rule.securityGroup.id || rule.securityGroup.name}>
                  <dt>{FirewallLabels.get('Firewall')}</dt>
                  <dd>
                    <UISref
                      to="^.firewallDetails"
                      params={{
                        accountId: rule.securityGroup.accountName || rule.securityGroup.accountId,
                        name: rule.securityGroup.name,
                        provider: 'ecs',
                        region: rule.securityGroup.region,
                        vpcId: rule.securityGroup.vpcId,
                      }}
                    >
                      <a>
                        {rule.securityGroup.accountName !== securityGroup.accountName && (
                          <AccountTag account={rule.securityGroup.accountName || rule.securityGroup.accountId} />
                        )}{' '}
                        {rule.securityGroup.name} ({rule.securityGroup.id})
                      </a>
                    </UISref>
                  </dd>
                  <dt>Port Ranges</dt>
                  {rule.rules.map((portRange) => (
                    <dd key={`${portRange.protocol}-${portRange.startPort}-${portRange.endPort}`}>
                      {portRange.protocol}: {portRange.startPort} &rarr; {portRange.endPort}
                    </dd>
                  ))}
                </dl>
              ))}
            </CollapsibleSection>
          </div>
        )}
      </div>
    );
  }
}

export const EcsSecurityGroupDetails = withRouter(EcsSecurityGroupDetailsComponent);
