import { cloneDeep } from 'lodash';
import React from 'react';
import type { SortableContainerProps, SortEnd } from 'react-sortable-hoc';
import { arrayMove, SortableContainer, SortableElement, SortableHandle } from 'react-sortable-hoc';

import { AccountService } from '../../../../account/AccountService';
import { AccountTag } from '../../../../account/AccountTag';
import type { IDeckRuntimeServicesInjectedProps } from '../../../../bootstrap/DeckRuntimeContext';
import { withDeckRuntimeServices } from '../../../../bootstrap/DeckRuntimeContext';
import { CloudProviderLogo } from '../../../../cloudProvider/CloudProviderLogo';
import { CloudProviderRegistry } from '../../../../cloudProvider/CloudProviderRegistry';
import type { IProviderSelectionFilter } from '../../../../cloudProvider/providerSelection/ProviderSelectionService';
import { ProviderSelectionService } from '../../../../cloudProvider/providerSelection/ProviderSelectionService';
import type { IStageConfigProps } from '../common';
import { StageConfigField } from '../common';
import { NameUtils } from '../../../../naming/nameUtils';
import { Markdown } from '../../../../presentation/Markdown';
import { StageConstants } from '../stageConstants';

export interface IDeployStageConfigState {
  clusters: any[];
  modalError: string;
  showProviderColumn: boolean;
}

export class DeployStageConfigComponent extends React.Component<
  IStageConfigProps & IDeckRuntimeServicesInjectedProps,
  IDeployStageConfigState
> {
  private mounted = true;
  private subnetRenderers: { [cloudProvider: string]: any } = {};

  constructor(props: IStageConfigProps & IDeckRuntimeServicesInjectedProps) {
    super(props);
    this.ensureStageDefaults(props);
    this.state = {
      clusters: props.stage.clusters,
      modalError: null,
      showProviderColumn: false,
    };
  }

  public componentDidMount(): void {
    AccountService.listProviders().then((providers) => {
      if (this.mounted) {
        this.setState({ showProviderColumn: providers && providers.length > 1 });
      }
    });
  }

  public componentWillReceiveProps(nextProps: IStageConfigProps): void {
    this.ensureStageDefaults(nextProps);
    this.setState({ clusters: nextProps.stage.clusters });
  }

  public componentWillUnmount(): void {
    this.mounted = false;
  }

  private ensureStageDefaults(props: IStageConfigProps): void {
    props.stage.clusters = props.stage.clusters || [];
    if (props.pipeline.strategy) {
      props.stage.trafficOptions = props.stage.trafficOptions || StageConstants.STRATEGY_TRAFFIC_OPTIONS[0].val;
    }
  }

  private updateClusters = (clusters: any[]): void => {
    this.props.stage.clusters = clusters;
    this.setState({ clusters });
    this.props.stageFieldUpdated();
  };

  private getRegion = (cluster: any): string => {
    if (cluster.region) {
      return cluster.region;
    }
    const availabilityZones = cluster.availabilityZones;
    if (availabilityZones) {
      const regions = Object.keys(availabilityZones);
      if (regions && regions.length) {
        return regions[0];
      }
    }
    return 'n/a';
  };

  private getCloudProvider(cluster: any): string {
    return cluster.cloudProvider || cluster.provider || cluster.providerType || 'aws';
  }

  private hasSubnetDeployments = (): boolean => {
    return this.state.clusters.some((cluster) =>
      CloudProviderRegistry.hasValue(this.getCloudProvider(cluster), 'subnet'),
    );
  };

  private hasInstanceTypeDeployments = (): boolean => {
    return this.state.clusters.some((cluster) => cluster.instanceType !== undefined);
  };

  private getSubnet = (cluster: any): string => {
    const cloudProvider = this.getCloudProvider(cluster);
    if (!CloudProviderRegistry.hasValue(cloudProvider, 'subnet')) {
      return '[none]';
    }

    const subnetRenderer = CloudProviderRegistry.getValue(cloudProvider, 'subnet').renderer;
    if (typeof subnetRenderer !== 'function') {
      throw new Error(`No subnet renderer found for provider "${cloudProvider}".`);
    }

    this.subnetRenderers[cloudProvider] = this.subnetRenderers[cloudProvider] || new subnetRenderer();
    return this.subnetRenderers[cloudProvider].render(cluster);
  };

  private getClusterName = (cluster: any): string =>
    NameUtils.getClusterName(cluster.application, cluster.stack, cluster.freeFormDetails);

  private showCloneServerGroupModal(provider: string, serverGroupConfig: any, command: any): PromiseLike<any> {
    const CloneServerGroupModal = serverGroupConfig && serverGroupConfig.CloneServerGroupModal;
    if (!CloneServerGroupModal) {
      const modalError = `No React clone server group modal is registered for provider "${provider}".`;
      this.setState({ modalError });
      return Promise.reject(new Error(modalError));
    }

    return CloneServerGroupModal.show(
      {
        title: 'Configure Deployment Cluster',
        application: this.props.application,
        command,
      },
      this.props.deckRuntimeServices,
    );
  }

  private providerFilterFn: IProviderSelectionFilter = (_application, _account, provider) => {
    return (
      Boolean(provider.serverGroup?.CloneServerGroupModal) &&
      (!provider.unsupportedStageTypes || provider.unsupportedStageTypes.indexOf('deploy') === -1)
    );
  };

  private addCluster = (): void => {
    this.setState({ modalError: null });
    ProviderSelectionService.selectProvider(this.props.application, 'serverGroup', this.providerFilterFn)
      .then((selectedProvider) => {
        const serverGroupConfig = CloudProviderRegistry.getValue(selectedProvider, 'serverGroup');
        return this.props.deckRuntimeServices.serverGroupCommandBuilder
          .buildNewServerGroupCommandForPipeline(selectedProvider, this.props.stage, this.props.pipeline)
          .then((command: any) => this.showCloneServerGroupModal(selectedProvider, serverGroupConfig, command))
          .then((command: any) => {
            command.provider = selectedProvider;
            const stageCluster = this.props.deckRuntimeServices.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(
              command,
            );
            delete stageCluster.credentials;
            this.updateClusters(this.state.clusters.concat(stageCluster));
          });
      })
      .catch((error: Error) => {
        if (error instanceof Error) {
          this.setState({ modalError: error.message });
        }
      });
  };

  private editCluster = (cluster: any, index: number): void => {
    this.setState({ modalError: null });
    cluster.provider = cluster.cloudProvider || cluster.providerType || 'aws';
    const providerConfig = CloudProviderRegistry.getProvider(cluster.provider);

    this.props.deckRuntimeServices.serverGroupCommandBuilder
      .buildServerGroupCommandFromPipeline(this.props.application, cluster, this.props.stage, this.props.pipeline)
      .then((command: any) =>
        this.showCloneServerGroupModal(cluster.provider, providerConfig && providerConfig.serverGroup, command),
      )
      .then((command: any) => {
        const stageCluster = this.props.deckRuntimeServices.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(
          command,
        );
        delete stageCluster.credentials;
        const clusters = this.state.clusters.slice(0);
        clusters[index] = stageCluster;
        this.updateClusters(clusters);
      })
      .catch(() => {});
  };

  private copyCluster = (index: number): void => {
    const clusters = this.state.clusters.concat(cloneDeep(this.state.clusters[index]));
    this.updateClusters(clusters);
  };

  private removeCluster = (index: number): void => {
    const clusters = this.state.clusters.slice(0);
    clusters.splice(index, 1);
    this.updateClusters(clusters);
  };

  private handleSortEnd = ({ oldIndex, newIndex }: SortEnd): void => {
    if (oldIndex !== newIndex) {
      this.updateClusters(arrayMove(this.state.clusters.slice(0), oldIndex, newIndex));
    }
  };

  private updateTrafficOptions = (event: React.ChangeEvent<HTMLSelectElement>): void => {
    this.props.stage.trafficOptions = event.target.value;
    this.props.stageFieldUpdated();
    this.forceUpdate();
  };

  private getTableColumnCount(showSubnetColumn: boolean, showInstanceTypeColumn: boolean): number {
    return 7 + (this.state.showProviderColumn ? 1 : 0) + (showSubnetColumn ? 1 : 0) + (showInstanceTypeColumn ? 1 : 0);
  }

  private renderStrategyConfig(): React.ReactNode {
    const selectedOption = StageConstants.STRATEGY_TRAFFIC_OPTIONS.find(
      (option) => option.val === this.props.stage.trafficOptions,
    );
    return (
      <StageConfigField label="Enable traffic">
        <select
          className="form-control input-sm"
          value={this.props.stage.trafficOptions}
          onChange={this.updateTrafficOptions}
        >
          {StageConstants.STRATEGY_TRAFFIC_OPTIONS.map((option) => (
            <option key={option.val} value={option.val}>
              {option.label}
            </option>
          ))}
        </select>
        {selectedOption && <Markdown message={selectedOption.description} />}
      </StageConfigField>
    );
  }

  public render(): React.ReactNode {
    if (this.props.pipeline.strategy) {
      return this.renderStrategyConfig();
    }

    const showSubnetColumn = this.hasSubnetDeployments();
    const showInstanceTypeColumn = this.hasInstanceTypeDeployments();

    return (
      <div className="row">
        <div className="col-md-12">
          {this.state.modalError && <div className="alert alert-danger">{this.state.modalError}</div>}
          <table className="table table-condensed table-deployStage">
            <thead>
              <tr>
                <th />
                {this.state.showProviderColumn && <th>Provider</th>}
                <th>Account</th>
                <th>Cluster</th>
                <th>Region</th>
                {showSubnetColumn && <th>Subnet</th>}
                <th>Strategy</th>
                <th>Capacity</th>
                {showInstanceTypeColumn && <th>Instance Type</th>}
                <th style={{ width: '58px' }}>Actions</th>
              </tr>
            </thead>
            <SortableClusterTableBody
              clusters={this.state.clusters}
              getCloudProvider={(cluster) => this.getCloudProvider(cluster)}
              getClusterName={this.getClusterName}
              getRegion={this.getRegion}
              getSubnet={this.getSubnet}
              showInstanceTypeColumn={showInstanceTypeColumn}
              showProviderColumn={this.state.showProviderColumn}
              showSubnetColumn={showSubnetColumn}
              editCluster={this.editCluster}
              copyCluster={this.copyCluster}
              removeCluster={this.removeCluster}
              onSortEnd={this.handleSortEnd}
              lockAxis="y"
              useDragHandle={true}
            />
            <tfoot>
              <tr>
                <td colSpan={this.getTableColumnCount(showSubnetColumn, showInstanceTypeColumn)}>
                  <button className="btn btn-block btn-sm add-new" type="button" onClick={this.addCluster}>
                    <span className="glyphicon glyphicon-plus-sign" data-test-id="Deploy.addServerGroup" /> Add server
                    group
                  </button>
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    );
  }
}

export const DeployStageConfig = withDeckRuntimeServices(DeployStageConfigComponent);

export interface IDeployClusterTableBodyProps extends SortableContainerProps {
  clusters: any[];
  copyCluster: (index: number) => void;
  editCluster: (cluster: any, index: number) => void;
  getCloudProvider: (cluster: any) => string;
  getClusterName: (cluster: any) => string;
  getRegion: (cluster: any) => string;
  getSubnet: (cluster: any) => string;
  removeCluster: (index: number) => void;
  showInstanceTypeColumn: boolean;
  showProviderColumn: boolean;
  showSubnetColumn: boolean;
}

const DragHandle = SortableHandle(() => (
  <span className="glyphicon glyphicon-resize-vertical" aria-label="Drag to reorder deployment cluster" />
));

const SortableClusterRow = SortableElement((props: IDeployClusterRowProps) => <DeployClusterRow {...props} />);

const SortableClusterTableBody = SortableContainer((props: IDeployClusterTableBodyProps) => (
  <tbody>
    {props.clusters.map((cluster, index) => (
      <SortableClusterRow
        key={`${props.getCloudProvider(cluster)}-${cluster.account}-${props.getRegion(cluster)}-${index}`}
        index={index}
        cluster={cluster}
        rowIndex={index}
        copyCluster={props.copyCluster}
        editCluster={props.editCluster}
        getCloudProvider={props.getCloudProvider}
        getClusterName={props.getClusterName}
        getRegion={props.getRegion}
        getSubnet={props.getSubnet}
        removeCluster={props.removeCluster}
        showInstanceTypeColumn={props.showInstanceTypeColumn}
        showProviderColumn={props.showProviderColumn}
        showSubnetColumn={props.showSubnetColumn}
      />
    ))}
  </tbody>
));

export interface IDeployClusterRowProps {
  cluster: any;
  copyCluster: (index: number) => void;
  editCluster: (cluster: any, index: number) => void;
  getCloudProvider: (cluster: any) => string;
  getClusterName: (cluster: any) => string;
  getRegion: (cluster: any) => string;
  getSubnet: (cluster: any) => string;
  removeCluster: (index: number) => void;
  rowIndex: number;
  showInstanceTypeColumn: boolean;
  showProviderColumn: boolean;
  showSubnetColumn: boolean;
}

const DeployClusterRow = (props: IDeployClusterRowProps) => {
  const { cluster, rowIndex } = props;
  const cloudProvider = props.getCloudProvider(cluster);
  return (
    <tr>
      <td className="handle">
        <DragHandle />
      </td>
      {props.showProviderColumn && (
        <td>
          <CloudProviderLogo provider={cloudProvider} width="20px" height="20px" showTooltip={true} />
        </td>
      )}
      <td>
        <AccountTag account={cluster.account} />
      </td>
      <td>{props.getClusterName(cluster)}</td>
      <td>{props.getRegion(cluster)}</td>
      {props.showSubnetColumn && <td>{props.getSubnet(cluster)}</td>}
      <td>{cluster.strategy || '[none]'}</td>
      <td>{renderCapacity(cluster)}</td>
      {props.showInstanceTypeColumn && <td>{cluster.instanceType || '[none]'}</td>}
      <td className="condensed-actions">
        <button
          className="btn btn-sm btn-link"
          type="button"
          title="Edit"
          onClick={() => props.editCluster(cluster, rowIndex)}
        >
          <span className="glyphicon glyphicon-edit" />
        </button>
        <button
          className="btn btn-sm btn-link pad-left"
          type="button"
          title="Duplicate"
          onClick={() => props.copyCluster(rowIndex)}
        >
          <span className="glyphicon glyphicon-duplicate" />
        </button>
        <button
          className="btn btn-sm btn-link pad-left"
          type="button"
          title="Remove"
          onClick={() => props.removeCluster(rowIndex)}
        >
          <span className="glyphicon glyphicon-trash" />
        </button>
      </td>
    </tr>
  );
};

function renderCapacity(cluster: any): React.ReactNode {
  if (cluster.useSourceCapacity) {
    return <div>Current Server Group</div>;
  }
  if (cluster.capacity) {
    if (cluster.capacity.min === cluster.capacity.max) {
      return <div>{cluster.capacity.max}</div>;
    }
    return (
      <div>
        Min: {cluster.capacity.min}, Max: {cluster.capacity.max}, Desired: {cluster.capacity.desired}
      </div>
    );
  }
  if (cluster.targetSize) {
    return <div>{cluster.targetSize}</div>;
  }
  return <div>N/A</div>;
}
