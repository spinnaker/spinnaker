import { UISref } from '@uirouter/react';
import { groupBy, isEmpty } from 'lodash';
import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import type {
  Application,
  IRouterInjectedProps,
  ISecurityGroup,
  ISecurityGroupDetail,
  SecurityGroupReader,
} from '@spinnaker/core';
import {
  AccountTag,
  AddEntityTagLinks,
  CollapsibleSection,
  ConfirmationModalService,
  confirmNotManaged,
  DeckRuntimeContext,
  FirewallLabels,
  ManagedResourceDetailsIndicator,
  RecentHistoryService,
  SecurityGroupWriter,
  SETTINGS,
  Spinner,
  withRouter,
} from '@spinnaker/core';

import { IPRangeRules } from './IPRangeRules';
import { AWSProviderSettings } from '../../aws.settings';
import { AmazonSecurityGroupModal } from '../configure/AmazonSecurityGroupModal';
import { VpcReader } from '../../vpc/VpcReader';

interface IAmazonResolvedSecurityGroup {
  accountId?: string;
  name: string;
  provider?: string;
  region: string;
  vpcId?: string;
}

interface IAmazonSecurityGroupDetailsProps {
  app: Application;
  autoClose?: () => void;
  resolvedSecurityGroup: IAmazonResolvedSecurityGroup;
  securityGroupReader?: SecurityGroupReader;
}

interface IAmazonSecurityGroupDetailsState {
  loading: boolean;
  notFound?: boolean;
  securityGroup?: ISecurityGroupDetail & ISecurityGroup & Record<string, any>;
}

export interface ISecurityGroupRuleModel {
  rules: ISecurityGroupPortRangeRule[];
  securityGroup: ISecurityGroup;
}

interface ISecurityGroupPortRangeRule {
  description: string;
  endPort: number;
  protocol: string;
  startPort: number;
}

function buildRuleModel(groupedRangeRules: any, address: string): ISecurityGroupPortRangeRule[] {
  const rules: ISecurityGroupPortRangeRule[] = [];
  groupedRangeRules[address].forEach((rule: any) => {
    (rule.portRanges || []).forEach((range: any) => {
      if (rule.protocol === '-1' || (range.startPort !== undefined && range.endPort !== undefined)) {
        rules.push({
          startPort: range.startPort,
          endPort: range.endPort,
          protocol: rule.protocol,
          description: rule.description || '',
        });
      }
    });
  });
  return rules;
}

export function buildIpRulesModel(details: any) {
  const groupedRangeRules = groupBy(details.ipRangeRules || [], (rule: any) => rule.range.ip + rule.range.cidr);
  return Object.keys(groupedRangeRules)
    .map((address) => ({ address, rules: buildRuleModel(groupedRangeRules, address) }))
    .filter((rule) => rule.rules.length);
}

export function buildSecurityGroupRulesModel(details: any): ISecurityGroupRuleModel[] {
  const groupedRangeRules = groupBy(details.securityGroupRules || [], (rule: any) => rule.securityGroup.id);
  return Object.keys(groupedRangeRules)
    .map((address) => ({
      securityGroup: groupedRangeRules[address][0].securityGroup,
      rules: buildRuleModel(groupedRangeRules, address),
    }))
    .filter((rule) => rule.rules.length);
}

function accountFor(securityGroup: any, resolvedSecurityGroup?: IAmazonResolvedSecurityGroup): string {
  return (
    securityGroup.accountId ||
    securityGroup.accountName ||
    securityGroup.account ||
    securityGroup.credentials ||
    resolvedSecurityGroup?.accountId
  );
}

function withResolvedCoordinates(securityGroup: any, resolvedSecurityGroup?: IAmazonResolvedSecurityGroup): any {
  if (!resolvedSecurityGroup) {
    return securityGroup;
  }

  const accountId = accountFor(securityGroup, resolvedSecurityGroup);
  return {
    ...securityGroup,
    accountId,
    accountName: securityGroup.accountName || accountId,
    name: securityGroup.name || resolvedSecurityGroup.name,
    region: securityGroup.region || resolvedSecurityGroup.region,
    vpcId: securityGroup.vpcId || resolvedSecurityGroup.vpcId,
  };
}

export function AmazonSecurityGroupActions({
  app,
  resolvedSecurityGroup,
  securityGroup,
}: {
  app: Application;
  resolvedSecurityGroup?: IAmazonResolvedSecurityGroup;
  securityGroup: any;
}): JSX.Element | null {
  if (!AWSProviderSettings.adHocInfraWritesEnabled) {
    return null;
  }

  const securityGroupWithCoordinates = withResolvedCoordinates(securityGroup, resolvedSecurityGroup);
  const editInboundRules = () => {
    confirmNotManaged(securityGroupWithCoordinates, app).then((notManaged) => {
      if (notManaged) {
        AmazonSecurityGroupModal.show({ app, mode: 'edit', securityGroup: securityGroupWithCoordinates });
      }
    });
  };
  const cloneSecurityGroup = () => {
    AmazonSecurityGroupModal.show({ app, mode: 'clone', securityGroup: securityGroupWithCoordinates });
  };
  const deleteSecurityGroup = () => {
    let isRetry = false;
    confirmNotManaged(securityGroupWithCoordinates, app).then((notManaged) => {
      if (!notManaged) {
        return;
      }

      ConfirmationModalService.confirm({
        header: `Really delete ${securityGroupWithCoordinates.name}?`,
        buttonText: `Delete ${securityGroupWithCoordinates.name}`,
        account: accountFor(securityGroupWithCoordinates),
        taskMonitorConfig: {
          application: app,
          title: `Deleting ${securityGroupWithCoordinates.name}`,
          onTaskRetry: () => {
            isRetry = true;
          },
        },
        submitMethod: () => {
          const params: any = {
            cloudProvider: securityGroupWithCoordinates.provider || 'aws',
            vpcId: securityGroupWithCoordinates.vpcId,
          };
          if (isRetry) {
            params.removeDependencies = true;
          }
          return SecurityGroupWriter.deleteSecurityGroup(securityGroupWithCoordinates, app, params);
        },
        retryBody: `<div><p>Retry deleting the ${FirewallLabels.get(
          'firewall',
        )} and revoke any dependent ingress rules?</p><p>Any instance or load balancer associations will have to removed manually.</p></div>`,
      });
    });
  };

  return (
    <Dropdown className="dropdown" id="amazon-security-group-actions-dropdown">
      <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">
        {FirewallLabels.get('Firewall')} Actions
      </Dropdown.Toggle>
      <Dropdown.Menu>
        <MenuItem onClick={editInboundRules}>Edit Inbound Rules</MenuItem>
        <MenuItem onClick={deleteSecurityGroup}>Delete {FirewallLabels.get('Firewall')}</MenuItem>
        <MenuItem onClick={cloneSecurityGroup}>Clone {FirewallLabels.get('Firewall')}</MenuItem>
        {SETTINGS.feature.entityTags && (
          <AddEntityTagLinks
            component={securityGroupWithCoordinates}
            application={app}
            entityType="securityGroup"
            onUpdate={() => app.securityGroups.refresh()}
          />
        )}
      </Dropdown.Menu>
    </Dropdown>
  );
}

export class AmazonSecurityGroupDetailsComponent extends React.Component<
  IAmazonSecurityGroupDetailsProps & IRouterInjectedProps,
  IAmazonSecurityGroupDetailsState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  public state: IAmazonSecurityGroupDetailsState = { loading: true };

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

  public componentDidUpdate(prevProps: IAmazonSecurityGroupDetailsProps): void {
    if (this.getCoordinatesKey(prevProps) !== this.getCoordinatesKey(this.props)) {
      this.loadSecurityGroup();
    }
  }

  public componentWillUnmount(): void {
    this.isUnmounted = true;
    if (this.unsubscribeFromRefresh) {
      this.unsubscribeFromRefresh();
    }
  }

  private getCoordinatesKey(props: IAmazonSecurityGroupDetailsProps): string {
    const { accountId, name, region, vpcId } = props.resolvedSecurityGroup;
    return [accountId, region, vpcId || '', name].join(':');
  }

  private getSecurityGroupReader(): SecurityGroupReader {
    return this.props.securityGroupReader || this.context.services.securityGroupReader;
  }

  private autoClose = (): void => {
    if (this.isUnmounted) {
      return;
    }
    if (this.props.autoClose) {
      this.props.autoClose();
      return;
    }
    if (this.props.app.isStandalone) {
      RecentHistoryService.removeLastItem('securityGroups');
      this.setState({ loading: false, notFound: true });
      return;
    }
    this.props.stateService.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
  };

  private loadSecurityGroup = (): void => {
    const { app, resolvedSecurityGroup } = this.props;
    const requestId = ++this.loadRequestId;
    this.setState({ loading: true, notFound: false });
    this.getSecurityGroupReader()
      .getSecurityGroupDetails(
        app,
        resolvedSecurityGroup.accountId,
        'aws',
        resolvedSecurityGroup.region,
        resolvedSecurityGroup.vpcId || '',
        resolvedSecurityGroup.name,
      )
      .then(
        (details: ISecurityGroupDetail & ISecurityGroup & Record<string, any>): Promise<void> => {
          if (this.isUnmounted || requestId !== this.loadRequestId) {
            return Promise.resolve();
          }
          if (!details || isEmpty(details)) {
            this.autoClose();
            return Promise.resolve();
          }
          return Promise.resolve(VpcReader.getVpcName(details.vpcId)).then((vpcName) => {
            if (this.isUnmounted || requestId !== this.loadRequestId) {
              return;
            }

            const applicationSecurityGroup = this.getSecurityGroupReader().getApplicationSecurityGroup(
              app,
              resolvedSecurityGroup.accountId,
              resolvedSecurityGroup.region,
              resolvedSecurityGroup.name,
            );
            this.setState({
              loading: false,
              securityGroup: withResolvedCoordinates(
                { ...applicationSecurityGroup, ...details, vpcName },
                resolvedSecurityGroup,
              ),
            });
          });
        },
      )
      .catch(() => {
        if (requestId === this.loadRequestId) {
          this.autoClose();
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
              <span className="glyphicon glyphicon-transfer" />
              <h3 className="horizontal middle space-between flex-1">{securityGroup.name || '(not found)'}</h3>
            </div>
          )}
          {!loading && securityGroup && (
            <div className="actions">
              <AmazonSecurityGroupActions
                app={app}
                resolvedSecurityGroup={resolvedSecurityGroup}
                securityGroup={securityGroup}
              />
            </div>
          )}
        </div>
        {!loading && securityGroup?.isManaged && (
          <ManagedResourceDetailsIndicator resourceSummary={securityGroup.managedResourceSummary} application={app} />
        )}
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
            <IPRangeRules ipRules={buildIpRulesModel(securityGroup)} />
            <CollapsibleSection
              heading={`${FirewallLabels.get('Firewall')} Rules (${
                buildSecurityGroupRulesModel(securityGroup).length || 0
              })`}
              defaultExpanded={buildSecurityGroupRulesModel(securityGroup).length > 0}
            >
              {!buildSecurityGroupRulesModel(securityGroup).length && <div>None</div>}
              {buildSecurityGroupRulesModel(securityGroup).map((rule) => (
                <dl className="dl-horizontal dl-medium" key={rule.securityGroup.id || rule.securityGroup.name}>
                  <dt>{FirewallLabels.get('Firewall')}</dt>
                  <dd>
                    <UISref
                      to="^.firewallDetails"
                      params={{
                        name: rule.securityGroup.name,
                        accountId: rule.securityGroup.accountName || rule.securityGroup.accountId,
                        region: rule.securityGroup.region,
                        vpcId: rule.securityGroup.vpcId,
                        provider: 'aws',
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

export const AmazonSecurityGroupDetails = withRouter(AmazonSecurityGroupDetailsComponent);
