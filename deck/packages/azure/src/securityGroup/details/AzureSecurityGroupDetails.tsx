import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import type {
  Application,
  IOverridableProps,
  IRouterInjectedProps,
  ISecurityGroupDetail,
  SecurityGroupReader,
} from '@spinnaker/core';
import {
  AccountTag,
  CollapsibleSection,
  ConfirmationModalService,
  DeckRuntimeContext,
  withRouter,
} from '@spinnaker/core';

import { AzureSecurityGroupModal } from '../configure/AzureSecurityGroupModal';
import { AzureSecurityGroupWriter } from '../securityGroup.write.service';

interface IAzureResolvedSecurityGroup {
  accountId?: string;
  name: string;
  provider?: string;
  region: string;
  vpcId?: string;
}

interface IAzureSecurityGroupDetailsProps extends IOverridableProps {
  app: Application;
  autoClose?: () => void;
  resolvedSecurityGroup: IAzureResolvedSecurityGroup;
  securityGroupReader?: SecurityGroupReader;
}

interface IAzureSecurityGroupDetailsState {
  loading: boolean;
  securityGroup?: ISecurityGroupDetail & Record<string, any>;
}

interface IAzureSecurityGroupSectionProps {
  securityGroup: ISecurityGroupDetail & Record<string, any>;
}

export class AzureSecurityGroupDetailsComponent extends React.Component<
  IAzureSecurityGroupDetailsProps & IRouterInjectedProps,
  IAzureSecurityGroupDetailsState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  public state: IAzureSecurityGroupDetailsState = { loading: true };

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

  public componentWillUnmount(): void {
    this.isUnmounted = true;
    if (this.unsubscribeFromRefresh) {
      this.unsubscribeFromRefresh();
    }
  }

  public componentDidUpdate(prevProps: IAzureSecurityGroupDetailsProps): void {
    if (this.getCoordinatesKey(prevProps) !== this.getCoordinatesKey(this.props)) {
      this.loadSecurityGroup();
    }
  }

  private getCoordinatesKey(props: IAzureSecurityGroupDetailsProps): string {
    const { accountId, name, region, vpcId } = props.resolvedSecurityGroup;
    return [accountId, region, vpcId || '', name].join(':');
  }

  private getSecurityGroupReader(): SecurityGroupReader {
    return this.props.securityGroupReader || this.context.services.securityGroupReader;
  }

  private loadSecurityGroup = (): void => {
    const { app, resolvedSecurityGroup } = this.props;
    const requestId = ++this.loadRequestId;
    this.setState({ loading: true });
    this.getSecurityGroupReader()
      .getSecurityGroupDetails(
        app,
        resolvedSecurityGroup.accountId,
        'azure',
        resolvedSecurityGroup.region,
        resolvedSecurityGroup.vpcId || '',
        resolvedSecurityGroup.name,
      )
      .then((securityGroup: ISecurityGroupDetail & Record<string, any>) => {
        if (this.isUnmounted || requestId !== this.loadRequestId) {
          return;
        }
        if (!securityGroup || Object.keys(securityGroup).length === 0) {
          this.autoClose();
          return;
        }
        this.setState({ loading: false, securityGroup });
      })
      .catch(() => {
        if (requestId === this.loadRequestId) {
          this.autoClose();
        }
      });
  };

  private autoClose = (): void => {
    if (this.isUnmounted) {
      return;
    }
    if (this.props.autoClose) {
      this.props.autoClose();
      return;
    }
    this.props.stateService.go('^');
  };

  private closeDetails = (): void => {
    this.props.stateService.go('^');
  };

  public render(): JSX.Element {
    const { app } = this.props;
    const { loading, securityGroup } = this.state;

    if (loading || !securityGroup) {
      return (
        <div className="details-panel">
          <div className="header">
            <div className="close-button">
              <a className="btn btn-link" onClick={this.closeDetails}>
                <span className="glyphicon glyphicon-remove" />
              </a>
            </div>
            <h4 className="text-center">Loading...</h4>
          </div>
        </div>
      );
    }

    return (
      <div className="details-panel">
        <div className="header">
          <div className="close-button">
            <a className="btn btn-link" onClick={this.closeDetails}>
              <span className="glyphicon glyphicon-remove" />
            </a>
          </div>
          <div className="header-text horizontal middle">
            <h3>{securityGroup.name}</h3>
          </div>
          <div className="actions">
            <AzureSecurityGroupActions
              app={app}
              resolvedSecurityGroup={this.props.resolvedSecurityGroup}
              securityGroup={securityGroup}
            />
          </div>
        </div>
        <div className="content">
          <AzureSecurityGroupInformationSection securityGroup={securityGroup} />
          <AzureSecurityGroupRulesSection securityGroup={securityGroup} />
        </div>
      </div>
    );
  }
}

export const AzureSecurityGroupDetails = withRouter(AzureSecurityGroupDetailsComponent);

function withResolvedCoordinates(securityGroup: any, resolvedSecurityGroup?: IAzureResolvedSecurityGroup): any {
  if (!resolvedSecurityGroup) {
    return securityGroup;
  }

  const accountId =
    securityGroup.accountId ||
    securityGroup.account ||
    securityGroup.accountName ||
    securityGroup.credentials ||
    resolvedSecurityGroup.accountId;
  const name = securityGroup.name || resolvedSecurityGroup.name;
  const region = securityGroup.region || resolvedSecurityGroup.region;
  const vpcId = securityGroup.vpcId || resolvedSecurityGroup.vpcId;

  if (
    accountId === securityGroup.accountId &&
    name === securityGroup.name &&
    region === securityGroup.region &&
    vpcId === securityGroup.vpcId
  ) {
    return securityGroup;
  }

  return { ...securityGroup, accountId, name, region, vpcId };
}

export function AzureSecurityGroupActions({
  app,
  resolvedSecurityGroup,
  securityGroup,
}: {
  app: Application;
  resolvedSecurityGroup?: IAzureResolvedSecurityGroup;
  securityGroup: any;
}) {
  const securityGroupWithCoordinates = withResolvedCoordinates(securityGroup, resolvedSecurityGroup);
  const editSecurityGroup = (): void => {
    AzureSecurityGroupModal.show({ app, mode: 'edit', securityGroup: securityGroupWithCoordinates });
  };
  const cloneSecurityGroup = (): void => {
    AzureSecurityGroupModal.show({ app, mode: 'clone', securityGroup: securityGroupWithCoordinates });
  };
  const deleteSecurityGroup = (): void => {
    ConfirmationModalService.confirm({
      header: `Really delete ${securityGroupWithCoordinates.name}?`,
      buttonText: `Delete ${securityGroupWithCoordinates.name}`,
      account: securityGroupWithCoordinates.accountId || securityGroupWithCoordinates.account,
      taskMonitorConfig: {
        application: app,
        title: `Deleting ${securityGroupWithCoordinates.name}`,
      },
      submitMethod: () =>
        AzureSecurityGroupWriter.deleteSecurityGroup(securityGroupWithCoordinates, app, {
          cloudProvider: 'azure',
          vpcId: securityGroupWithCoordinates.vpcId,
        }),
    });
  };

  return (
    <Dropdown className="dropdown" id="azure-security-group-actions-dropdown">
      <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Security Group Actions</Dropdown.Toggle>
      <Dropdown.Menu>
        <MenuItem onClick={editSecurityGroup}>Edit Inbound Rules</MenuItem>
        <MenuItem onClick={cloneSecurityGroup}>Clone Security Group</MenuItem>
        <MenuItem onClick={deleteSecurityGroup}>Delete Security Group</MenuItem>
      </Dropdown.Menu>
    </Dropdown>
  );
}

export function AzureSecurityGroupInformationSection({ securityGroup }: IAzureSecurityGroupSectionProps) {
  return (
    <CollapsibleSection heading="Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>ID</dt>
        <dd>{securityGroup.id || '-'}</dd>
        <dt>Account</dt>
        <dd>
          <AccountTag account={securityGroup.accountId || securityGroup.accountName || securityGroup.account} />
        </dd>
        <dt>Region</dt>
        <dd>{securityGroup.region}</dd>
        <dt>Description:</dt>
        <dd>{securityGroup.description || '-'}</dd>
        <dt>VNet</dt>
        <dd>{securityGroup.vpcId || '-'}</dd>
      </dl>
    </CollapsibleSection>
  );
}

function joinRuleValues(...values: any[]): string {
  for (const value of values) {
    if (Array.isArray(value) && value.length) {
      return value.join(', ');
    }
    if (typeof value === 'string' && value.trim()) {
      return value.trim();
    }
  }
  return '-';
}

function ruleSources(rule: any): string {
  return joinRuleValues(
    rule.sourceAddressPrefixModel,
    rule.sourceAddressPrefixes,
    rule.sourceAddressPrefix,
    rule.sourceIPCIDRRanges,
    rule.sourceCidrs,
  );
}

function rulePorts(rule: any): string {
  return joinRuleValues(
    rule.destinationPortRangeModel,
    rule.destinationPortRanges,
    rule.destinationPortRange,
    rule.destPortRanges,
    rule.destinationPorts,
  );
}

export function AzureSecurityGroupRulesSection({ securityGroup }: IAzureSecurityGroupSectionProps) {
  const rules = (securityGroup.securityRules || securityGroup.inboundRules || [])
    .slice()
    .sort((a: any, b: any) => (a.priority ?? Number.MAX_SAFE_INTEGER) - (b.priority ?? Number.MAX_SAFE_INTEGER));
  return (
    <CollapsibleSection heading="Inbound Rules" defaultExpanded={true}>
      <table className="table table-condensed">
        <thead>
          <tr>
            <th>Priority</th>
            <th>Name</th>
            <th>Source</th>
            <th>Source Port</th>
            <th>Destination</th>
            <th>Ports</th>
            <th>Protocol</th>
            <th>Access</th>
            <th>Direction</th>
          </tr>
        </thead>
        <tbody>
          {rules.map((rule: any, index: number) => (
            <tr key={rule.name || index}>
              <td>{rule.priority || '-'}</td>
              <td>{rule.name || '-'}</td>
              <td>{ruleSources(rule)}</td>
              <td>{rule.sourcePortRange || '-'}</td>
              <td>{rule.destinationAddressPrefix || '-'}</td>
              <td>{rulePorts(rule)}</td>
              <td>{rule.protocol || '-'}</td>
              <td>{rule.access || '-'}</td>
              <td>{rule.direction || '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </CollapsibleSection>
  );
}
