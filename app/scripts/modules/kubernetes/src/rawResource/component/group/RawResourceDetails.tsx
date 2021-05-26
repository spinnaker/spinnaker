import { UISref } from '@uirouter/react';
import { get } from 'lodash';
import { DateTime } from 'luxon';
import React from 'react';
import { Dropdown } from 'react-bootstrap';
import { Subject } from 'rxjs';

import {
  AccountTag,
  CloudProviderLogo,
  CollapsibleSection,
  ConfirmationModalService,
  IManifest,
  logger,
  ManifestWriter,
  Overridable,
  Spinner,
  timestamp,
} from '@spinnaker/core';

import { RawResourceUtils } from '../RawResourceUtils';
import { ManifestLabels } from '../../../manifest/ManifestLabels';
import { KubernetesManifestService } from '../../../manifest/manifest.service';
import { KubernetesManifestCommandBuilder } from '../../../manifest/manifestCommandBuilder.service';
import { IKubernetesManifestCondition, ManifestCondition } from '../../../manifest/status/ManifestCondition';
import { ManifestWizard } from '../../../manifest/wizard/ManifestWizard';
import { ManifestEvents } from '../../../pipelines/stages/deployManifest/manifestStatus/ManifestEvents';

export interface IRawResourceDetailsProps {
  app: any;
  $stateParams: {
    account: string;
    application: string;
    name: string;
    region: string;
  };
}

export interface IRawResourcesDetailState {
  account: string;
  application: string;
  name: string;
  region: string;
  loading: boolean;
  manifest?: IManifest;
}

Overridable('k8s.rawResource.details');
export class RawResourceDetails extends React.Component<IRawResourceDetailsProps, IRawResourcesDetailState> {
  constructor(props: IRawResourceDetailsProps) {
    super(props);
    this.state = {
      account: null,
      application: null,
      name: null,
      region: null,
      loading: true,
      manifest: null,
    };
  }
  private destroy$ = new Subject();
  private props$ = new Subject<IRawResourceDetailsProps>();
  private unsubscribeManifest?: () => void;

  public componentDidMount() {
    this.setState({
      account: this.props.$stateParams.account,
      application: this.props.$stateParams.application,
      name: this.props.$stateParams.name,
      region: this.props.$stateParams.region,
    });
    const params = {
      account: this.props.$stateParams.account,
      location: this.props.$stateParams.region == '' ? '_' : this.props.$stateParams.region,
      name: this.props.$stateParams.name,
    };
    this.unsubscribeManifest = KubernetesManifestService.subscribe(this.props.app, params, (manifest) => {
      if (this.unsubscribeManifest != null) {
        this.setState({ manifest, loading: false });
      }
    });
  }

  public componentWillReceiveProps(nextProps: IRawResourceDetailsProps) {
    this.props$.next(nextProps);
  }

  public componentWillUnmount() {
    this.destroy$.next();
    if (this.unsubscribeManifest) {
      this.unsubscribeManifest();
      this.unsubscribeManifest = null;
    }
  }

  private editRawResource() {
    const { app } = this.props;
    const { account, application, manifest } = this.state;

    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      app,
      manifest.manifest,
      { app: application },
      account,
    ).then((builtCommand) => {
      ManifestWizard.show({ title: 'Edit Manifest', application: app, command: builtCommand });
    });
  }

  private deletedRawResource = () => {
    const { app } = this.props;
    const { account, manifest } = this.state;
    const name = manifest?.manifest?.metadata?.name ?? null;
    const kind = manifest?.manifest?.kind;
    const taskMonitor = {
      application: app,
      title: 'Deleting ' + name,
    };

    const submitMethod = () => {
      logger.log({ category: 'RawResource', action: 'Delete clicked' });
      const command = {
        manifestName: this.state.name,
        location: this.state.region,
        account: this.state.account,
        reason: null as string,
        cloudProvider: 'kubernetes',
        options: {
          cascading: false,
        },
      };
      return ManifestWriter.deleteManifest(command, this.props.app);
    };

    ConfirmationModalService.confirm({
      header: 'Are you sure you want to delete the ' + kind + ' ' + name + '?',
      buttonText: 'Delete',
      account: account,
      taskMonitorConfig: taskMonitor,
      submitMethod,
    });
  };

  private handleEditClick = (): void => {
    logger.log({ category: 'RawResource', action: 'Edit clicked' });
    this.editRawResource();
  };

  public render() {
    const { account, region, manifest } = this.state;
    const kind = manifest?.manifest?.kind;
    const apiVersion = manifest?.manifest?.apiVersion;
    const metadata = manifest?.manifest?.metadata;
    const name = metadata?.name;
    const creationUnixMs =
      get(metadata, 'creationTimestamp') && DateTime.fromISO(metadata.creationTimestamp).toMillis();

    return (
      <div className="details-panel">
        <div className="header">
          <div className="close-button">
            <UISref to="^">
              <span className="glyphicon glyphicon-remove" />
            </UISref>
          </div>
          {this.state.loading ? (
            <div className="horizontal center middle">
              <Spinner size="small" />
            </div>
          ) : (
            <div className="header-text horizontal middle">
              <CloudProviderLogo provider="kubernetes" height="36px" width="36px" />
              <h3 className="horizontal middle space-between flex-1">
                {kind} {name}
              </h3>
            </div>
          )}
          {!this.state.loading && (
            <div className="actions">
              <Dropdown className="dropdown" id="resource-actions-dropdown">
                <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">
                  <span>{kind} Actions</span>
                </Dropdown.Toggle>
                <Dropdown.Menu>
                  <li key="action-edit" id="resource-action-edit">
                    <a onClick={this.handleEditClick}>Edit</a>
                  </li>
                  <li key="action-delete" id="resource-action-delete">
                    <a onClick={this.deletedRawResource}>Delete</a>
                  </li>
                </Dropdown.Menu>
              </Dropdown>
            </div>
          )}
        </div>
        {!this.state.loading && (
          <div className="content">
            <CollapsibleSection heading="Information" defaultExpanded={true}>
              <dl className="dl-horizontal dl-narrow">
                <dt>Created</dt>
                <dd>{timestamp(creationUnixMs)}</dd>
                <dt>Account</dt>
                <dd>
                  <AccountTag account={account} />
                </dd>
                <dt>API Version</dt>
                <dd>{apiVersion}</dd>
                <dt>Kind</dt>
                <dd>{kind}</dd>
                <dt>Namespace</dt>
                <dd>{RawResourceUtils.namespaceDisplayName(region)}</dd>
              </dl>
            </CollapsibleSection>
            <CollapsibleSection key="status" heading="status" defaultExpanded={true}>
              <ul>
                {(manifest?.manifest?.status?.conditions ?? []).map((condition: IKubernetesManifestCondition) => (
                  <li key={condition.type + condition.lastTransitionTime} style={{ marginBottom: '10px' }}>
                    <ManifestCondition condition={condition} />
                  </li>
                ))}
              </ul>
            </CollapsibleSection>
            <CollapsibleSection key="events" heading="events" defaultExpanded={true}>
              <ManifestEvents manifest={manifest} />
            </CollapsibleSection>
            <CollapsibleSection key="labels" heading="labels" defaultExpanded={true}>
              <ManifestLabels manifest={get(manifest, ['manifest'], {})} />
            </CollapsibleSection>
          </div>
        )}
      </div>
    );
  }
}
