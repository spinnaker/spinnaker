import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import type {
  Application,
  IManifest,
  IOverridableProps,
  IRouterInjectedProps,
  ISecurityGroupDetail,
  SecurityGroupReader,
} from '@spinnaker/core';
import {
  AccountTag,
  AddEntityTagLinks,
  CloudProviderLogo,
  CollapsibleSection,
  DeckRuntimeContext,
  EntityNotifications,
  FirewallLabels,
  ManifestReader,
  robotToHuman,
  SETTINGS,
  timestamp,
  useModal,
  withRouter,
} from '@spinnaker/core';

import type { IKubernetesSecurityGroup } from '../../interfaces';
import { AnnotationCustomSections } from '../../manifest/AnnotationCustomSections';
import { ManifestLabels } from '../../manifest/ManifestLabels';
import { DeleteModal } from '../../manifest/delete/DeleteModal';
import { KubernetesManifestCommandBuilder } from '../../manifest/manifestCommandBuilder.service';
import { ManifestWizard } from '../../manifest/wizard/ManifestWizard';

interface ISecurityGroupFromStateParams {
  accountId: string;
  name: string;
  region: string;
}

export interface IKubernetesSecurityGroupDetailsProps extends IOverridableProps {
  app: Application;
  autoClose?: () => void;
  resolvedSecurityGroup: ISecurityGroupFromStateParams;
  securityGroupReader?: SecurityGroupReader;
}

interface IKubernetesSecurityGroupDetailsState {
  loading: boolean;
  manifest?: IManifest;
  securityGroup?: IKubernetesSecurityGroup;
}

export interface IKubernetesSecurityGroupActionsProps {
  app: Application;
  manifest: IManifest;
  securityGroup: IKubernetesSecurityGroup;
}

export class KubernetesSecurityGroupDetailsComponent extends React.Component<
  IKubernetesSecurityGroupDetailsProps & IRouterInjectedProps,
  IKubernetesSecurityGroupDetailsState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  public state: IKubernetesSecurityGroupDetailsState = {
    loading: true,
  };

  private isUnmounted = false;
  private dataSourceReady = false;
  private loadRequestId = 0;
  private unsubscribeFromRefresh: () => void;

  public componentDidMount(): void {
    const dataSource = this.props.app.getDataSource('securityGroups');

    if (!dataSource) {
      this.dataSourceReady = true;
      this.loadSecurityGroup();
      return;
    }

    dataSource
      .ready()
      .then(() => {
        if (this.isUnmounted) {
          return;
        }

        this.dataSourceReady = true;
        this.loadSecurityGroup();
        this.unsubscribeFromRefresh = dataSource.onRefresh(null, this.loadSecurityGroup);
      })
      .catch(this.autoClose);
  }

  public componentDidUpdate(prevProps: IKubernetesSecurityGroupDetailsProps): void {
    if (this.dataSourceReady && this.getCoordinatesKey(prevProps) !== this.getCoordinatesKey(this.props)) {
      this.loadSecurityGroup();
    }
  }

  public componentWillUnmount(): void {
    this.isUnmounted = true;
    if (this.unsubscribeFromRefresh) {
      this.unsubscribeFromRefresh();
    }
  }

  private getSecurityGroupReader(): SecurityGroupReader {
    return this.props.securityGroupReader || this.context.services.securityGroupReader;
  }

  private getCoordinatesKey(props: IKubernetesSecurityGroupDetailsProps): string {
    const { accountId, name, region } = props.resolvedSecurityGroup;
    return [accountId, region, name].join(':');
  }

  private loadSecurityGroup = (): void => {
    const { app, resolvedSecurityGroup } = this.props;
    const { accountId, name, region } = resolvedSecurityGroup;
    const requestId = ++this.loadRequestId;

    this.setState({ loading: true });

    Promise.all([
      this.getSecurityGroupReader().getSecurityGroupDetails(app, accountId, 'kubernetes', region, '', name),
      ManifestReader.getManifest(accountId, region, name),
    ])
      .then(([securityGroup, manifest]: [ISecurityGroupDetail, IManifest]) => {
        if (!this.isCurrentLoad(requestId)) {
          return;
        }

        if (!securityGroup) {
          this.autoClose();
          return;
        }

        this.setState({
          loading: false,
          manifest,
          securityGroup: securityGroup as IKubernetesSecurityGroup,
        });
      })
      .catch(() => {
        if (this.isCurrentLoad(requestId)) {
          this.autoClose();
        }
      });
  };

  private isCurrentLoad(requestId: number): boolean {
    return !this.isUnmounted && requestId === this.loadRequestId;
  }

  private autoClose = (): void => {
    if (this.isUnmounted) {
      return;
    }

    if (this.props.autoClose) {
      this.props.autoClose();
      return;
    }

    this.props.stateService.params.allowModalToStayOpen = true;
    this.props.stateService.go('^', null, { location: 'replace' });
  };

  private closeDetails = (): void => {
    this.props.stateService.go('^');
  };

  public render(): JSX.Element {
    const { app } = this.props;
    const { loading, manifest, securityGroup } = this.state;

    if (loading || !manifest || !securityGroup) {
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

    const manifestBody = manifest.manifest;

    return (
      <div className="details-panel">
        <div className="header">
          <div className="close-button">
            <a className="btn btn-link" onClick={this.closeDetails}>
              <span className="glyphicon glyphicon-remove" />
            </a>
          </div>
          <div className="header-text horizontal middle">
            <CloudProviderLogo provider="kubernetes" height="36px" width="36px" />
            <h3 className="horizontal middle space-between flex-1">
              {securityGroup.displayName}
              {SETTINGS.feature.entityTags && (
                <EntityNotifications
                  entity={securityGroup}
                  application={app}
                  placement="bottom"
                  hOffsetPercent="90%"
                  entityType="securityGroup"
                  pageLocation="details"
                  onUpdate={() => app.securityGroups.refresh()}
                />
              )}
            </h3>
          </div>
          <div className="actions">
            <KubernetesSecurityGroupActions app={app} manifest={manifest} securityGroup={securityGroup} />
          </div>
        </div>

        <div className="content">
          <CollapsibleSection heading="Information" defaultExpanded={true}>
            <dl className="dl-horizontal dl-narrow">
              <dt>Created</dt>
              <dd>{timestamp(securityGroup.createdTime)}</dd>
              <dt>Account</dt>
              <dd>
                <AccountTag account={securityGroup.account} />
              </dd>
              <dt>Namespace</dt>
              <dd>{securityGroup.namespace}</dd>
              <dt>Kind</dt>
              <dd>{securityGroup.kind}</dd>
            </dl>
          </CollapsibleSection>

          <AnnotationCustomSections manifest={manifestBody} resource={securityGroup} />

          <CollapsibleSection heading="Labels" defaultExpanded={true}>
            <ManifestLabels manifest={manifestBody} />
          </CollapsibleSection>
        </div>
      </div>
    );
  }
}

export const KubernetesSecurityGroupDetails = withRouter(KubernetesSecurityGroupDetailsComponent);

export function KubernetesSecurityGroupActions({ app, manifest, securityGroup }: IKubernetesSecurityGroupActionsProps) {
  const deleteModal = useModal();
  const showEntityTags = SETTINGS.feature && SETTINGS.feature.entityTags;

  if (!SETTINGS.kubernetesAdHocInfraWritesEnabled) {
    return null;
  }

  const editSecurityGroup = (): void => {
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      app,
      manifest.manifest,
      securityGroup.moniker,
      securityGroup.account,
    ).then((builtCommand) => {
      ManifestWizard.show({ title: 'Edit Manifest', application: app, command: builtCommand });
    });
  };

  return (
    <>
      <Dropdown className="dropdown" id="security-group-actions-dropdown">
        <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">
          {robotToHuman(securityGroup.kind)} Actions
        </Dropdown.Toggle>
        <Dropdown.Menu>
          <MenuItem onClick={deleteModal.show}>Delete {FirewallLabels.get('Firewall')}</MenuItem>
          <MenuItem onClick={editSecurityGroup}>Edit {FirewallLabels.get('Firewall')}</MenuItem>
          {showEntityTags && (
            <AddEntityTagLinks
              component={securityGroup}
              application={app}
              entityType="securityGroup"
              onUpdate={() => app.securityGroups.refresh()}
            />
          )}
        </Dropdown.Menu>
      </Dropdown>
      <DeleteModal
        application={app}
        resource={securityGroup}
        manifestController={undefined}
        isOpen={deleteModal.open}
        dismissModal={deleteModal.close}
      />
    </>
  );
}
