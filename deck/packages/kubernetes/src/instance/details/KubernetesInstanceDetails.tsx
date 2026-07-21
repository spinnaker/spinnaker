import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import type { Application, IManifest, IMoniker } from '@spinnaker/core';
import {
  AccountTag,
  AngularServices,
  CollapsibleSection,
  ConsoleOutputLink,
  InstanceDetailsHeader,
  InstanceLinks,
  InstanceReader,
  ManifestReader,
  RecentHistoryService,
  robotToHuman,
  SETTINGS,
  timestamp,
  useModal,
} from '@spinnaker/core';

import type { IKubernetesInstance } from '../../interfaces';
import { findKubernetesInstanceIdentifier } from './kubernetesInstanceDetails.utils';
import { AnnotationCustomSections } from '../../manifest/AnnotationCustomSections';
import { ManifestLabels } from '../../manifest/ManifestLabels';
import { ManifestQos } from '../../manifest/ManifestQos';
import { ManifestResources } from '../../manifest/ManifestResources';
import { DeleteModal } from '../../manifest/delete/DeleteModal';
import { KubernetesManifestCommandBuilder } from '../../manifest/manifestCommandBuilder.service';
import { ManifestCondition } from '../../manifest/status/ManifestCondition';
import { ManifestWizard } from '../../manifest/wizard/ManifestWizard';
import { ManifestEvents } from '../../pipelines/stages/deployManifest/manifestStatus/ManifestEvents';

interface IInstanceFromStateParams {
  instanceId: string;
}

export interface IKubernetesInstanceDetailsProps {
  $stateParams?: {
    instanceId?: string;
  };
  app: Application;
  autoClose?: () => void;
  environment?: string;
  instance?: IInstanceFromStateParams;
  moniker?: IMoniker;
}

interface IConsoleOutputInstance {
  account: string;
  region: string;
  id: string;
  provider: string;
}

interface IKubernetesInstanceDetailsState {
  instance?: IKubernetesInstance;
  loading: boolean;
  manifest?: IKubernetesInstanceManifest;
}

interface IKubernetesInstanceManifest extends IManifest {
  metrics?: any;
}

export interface IKubernetesInstanceActionsProps {
  app: Application;
  instance: IKubernetesInstance;
  manifest: IManifest;
}

export class KubernetesInstanceDetails extends React.Component<
  IKubernetesInstanceDetailsProps,
  IKubernetesInstanceDetailsState
> {
  protected readonly renderActions: boolean = true;
  protected readonly instanceLinksPlacement: 'afterInformation' | 'afterSections' = 'afterSections';

  public state: IKubernetesInstanceDetailsState = {
    loading: true,
  };

  private isUnmounted = false;
  private appReady = false;
  private loadRequestId = 0;
  private unsubscribeFromRefresh: () => void;

  public componentDidMount(): void {
    this.props.app
      .ready()
      .then(() => {
        if (this.isUnmounted) {
          return;
        }

        this.appReady = true;
        this.loadInstance();
        this.unsubscribeFromRefresh = this.props.app.onRefresh(null, this.loadInstance);
      })
      .catch(this.autoClose);
  }

  public componentDidUpdate(prevProps: IKubernetesInstanceDetailsProps): void {
    if (this.appReady && this.getInstanceId(prevProps) !== this.getInstanceId(this.props)) {
      this.loadInstance();
    }
  }

  public componentWillUnmount(): void {
    this.isUnmounted = true;
    if (this.unsubscribeFromRefresh) {
      this.unsubscribeFromRefresh();
    }
  }

  private getInstanceId = (props: IKubernetesInstanceDetailsProps): string =>
    props.instance?.instanceId || props.$stateParams?.instanceId;

  private loadInstance = (): void => {
    const { app } = this.props;
    const instanceId = this.getInstanceId(this.props);
    const requestId = ++this.loadRequestId;
    const identifier = findKubernetesInstanceIdentifier(app, instanceId, (extraData) =>
      RecentHistoryService.addExtraDataToLatest('instances', extraData),
    );

    if (!identifier) {
      this.autoClose();
      return;
    }

    this.setState({ loading: true });

    Promise.all([
      InstanceReader.getInstanceDetails(identifier.account, identifier.namespace, identifier.name).then(
        (instanceDetails: IKubernetesInstance) => ({
          ...instanceDetails,
          id: identifier.id,
          name: identifier.name,
          provider: 'kubernetes',
        }),
      ),
      ManifestReader.getManifest(identifier.account, identifier.namespace, identifier.name).then(
        (manifest) => manifest as IKubernetesInstanceManifest,
      ),
    ])
      .then(([instance, manifest]) => {
        if (this.isCurrentLoad(requestId)) {
          this.setState({ instance, manifest, loading: false });
        }
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

    AngularServices.$state.params.allowModalToStayOpen = true;
    AngularServices.$state.go('^', null, { location: 'replace' });
  };

  private buildConsoleOutputInstance(instance: IKubernetesInstance): IConsoleOutputInstance {
    return {
      account: instance.account,
      region: instance.zone || instance.namespace,
      id: instance.humanReadableName || instance.name,
      provider: instance.provider || instance.cloudProvider || 'kubernetes',
    };
  }

  public render(): JSX.Element {
    const { app } = this.props;
    const { instance, loading, manifest } = this.state;

    if (loading || !instance || !manifest) {
      return (
        <div className="details-panel">
          <div className="header">
            <InstanceDetailsHeader
              cloudProvider="kubernetes"
              healthState=""
              instanceId={this.getInstanceId(this.props)}
              loading={true}
              standalone={app.isStandalone}
            />
          </div>
        </div>
      );
    }

    const manifestBody = manifest.manifest;
    const statusConditions = manifestBody?.status?.conditions || [];

    return (
      <div className="details-panel">
        <div className="header">
          <InstanceDetailsHeader
            cloudProvider={instance.cloudProvider || instance.provider || 'kubernetes'}
            healthState={instance.healthState}
            instanceId={instance.displayName || instance.humanReadableName || instance.name}
            loading={false}
            standalone={app.isStandalone}
          />
          {this.renderActions && (
            <div className="actions">
              <KubernetesInstanceActions app={app} instance={instance} manifest={manifest} />
            </div>
          )}
        </div>

        <div className="content">
          {this.renderInformationSection(instance, manifest)}
          {this.renderInstanceLinks('afterInformation', instance)}
          <CollapsibleSection heading="Status" defaultExpanded={true}>
            {statusConditions.map((condition: any, index: number) => (
              <ul key={`${condition.type || 'condition'}-${index}`}>
                <ManifestCondition condition={condition} />
              </ul>
            ))}
          </CollapsibleSection>
          <AnnotationCustomSections manifest={manifestBody} resource={instance} />
          <CollapsibleSection heading="Events" defaultExpanded={true}>
            <ManifestEvents manifest={manifest} />
          </CollapsibleSection>
          <CollapsibleSection heading="Resources" defaultExpanded={true}>
            <ManifestResources manifest={manifestBody} metrics={manifest.metrics as any} />
          </CollapsibleSection>
          <CollapsibleSection heading="Labels" defaultExpanded={true}>
            <ManifestLabels manifest={manifestBody} />
          </CollapsibleSection>
          {this.renderInstanceLinks('afterSections', instance)}
        </div>
      </div>
    );
  }

  private renderInstanceLinks(
    placement: 'afterInformation' | 'afterSections',
    instance: IKubernetesInstance,
  ): JSX.Element | null {
    const { app, environment } = this.props;

    if (this.instanceLinksPlacement !== placement) {
      return null;
    }

    return (
      <InstanceLinks
        address={instance.publicDnsName}
        application={app}
        instance={instance}
        moniker={instance.moniker}
        environment={environment}
      />
    );
  }

  private renderInformationSection(instance: IKubernetesInstance, manifest: IManifest): JSX.Element {
    const manifestBody = manifest.manifest;

    return (
      <CollapsibleSection heading="Information" defaultExpanded={true}>
        <dl className="dl-horizontal dl-narrow">
          <dt>Created</dt>
          <dd>{timestamp(instance.createdTime)}</dd>
          <dt>Account</dt>
          <dd>
            <AccountTag account={instance.account} />
          </dd>
          <dt>Namespace</dt>
          <dd>{instance.namespace}</dd>
          <dt>Kind</dt>
          <dd>{instance.kind}</dd>
          <dt>QOS Class</dt>
          <dd>
            <ManifestQos manifest={manifestBody} />
          </dd>
          <dt>Logs</dt>
          <dd>
            <ConsoleOutputLink instance={this.buildConsoleOutputInstance(instance) as any} usesMultiOutput={true} />
          </dd>
        </dl>
      </CollapsibleSection>
    );
  }
}

export function KubernetesInstanceActions({ app, instance, manifest }: IKubernetesInstanceActionsProps) {
  const deleteModal = useModal();

  if (!SETTINGS.kubernetesAdHocInfraWritesEnabled) {
    return null;
  }

  const editInstance = (): void => {
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      app,
      manifest.manifest,
      instance.moniker,
      instance.account,
    ).then((builtCommand) => {
      ManifestWizard.show({ title: 'Edit Manifest', application: app, command: builtCommand });
    });
  };

  return (
    <>
      <Dropdown className="dropdown" id="instance-actions-dropdown">
        <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">
          {robotToHuman(instance.kind)} Actions
        </Dropdown.Toggle>
        <Dropdown.Menu>
          <MenuItem onClick={deleteModal.show}>Delete</MenuItem>
          <MenuItem onClick={editInstance}>Edit</MenuItem>
        </Dropdown.Menu>
      </Dropdown>
      <DeleteModal
        application={app}
        resource={instance}
        manifestController={undefined}
        isOpen={deleteModal.open}
        dismissModal={deleteModal.close}
      />
    </>
  );
}
