import { groupBy, sortBy } from 'lodash';
import React from 'react';
import { Modal } from 'react-bootstrap';
import { Option } from 'react-select';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  AccountTag,
  Application,
  ILoadBalancer,
  IManifest,
  ISecurityGroup,
  IServerGroup,
  IServerGroupManager,
  ManifestReader,
  ModalClose,
  noop,
  robotToHuman,
  TetheredSelect,
} from '@spinnaker/core';

export interface IManifestCopierProps {
  application: Application;
  cloudProvider: string;
  show: boolean;
  onDismiss: () => void;
  onManifestSelected: (manifest: IManifest) => void;
}

export interface IManifestCopierState {
  selectedManifest: IManifestOption;
  manifests: IManifestOption[];
}

export interface IManifestOption {
  account: string;
  location: string;
  name: string;
  kind: string;
  data: any;
  key: string;
}

const LAST_APPLIED_CONFIGURATION = 'kubectl.kubernetes.io/last-applied-configuration';

/*
 * A modal that allows a user to copy a running Kubernetes resource.
 **/
export class ManifestCopier extends React.Component<IManifestCopierProps, IManifestCopierState> {
  private destroy$ = new Subject();

  public static getState = (props: IManifestCopierProps) => {
    let manifests: IManifestOption[] = [];

    const serverGroups: IServerGroup[] = props.application.getDataSource('serverGroups').data.filter(
      (s: IServerGroup) =>
        s.cloudProvider === props.cloudProvider &&
        s.category === 'serverGroup' &&
        // Don't include managed server groups - just the manager (see below).
        !(s.serverGroupManagers || []).length,
    );

    const grouped = groupBy(serverGroups, (serverGroup) =>
      [serverGroup.cluster, serverGroup.account, serverGroup.region].join(':'),
    );

    Object.keys(grouped).forEach((key) => {
      // Only include the most recent server group in a cluster (e.g., if v001 and v002 exist, only include v002).
      const latest = sortBy(grouped[key], 'name').pop();
      const [kind, name] = latest.name.split(' ');
      manifests.push({
        account: latest.account,
        location: latest.region,
        name: name,
        kind: kind,
        data: latest,
        key: [latest.account, latest.region, latest.name].join(':'),
      });
    });

    const serverGroupManagers: IServerGroupManager[] = props.application.getDataSource('serverGroupManagers').data;
    const loadBalancers: ILoadBalancer[] = props.application.getDataSource('loadBalancers').data;
    const firewalls: ISecurityGroup[] = props.application.getDataSource('securityGroups').data;

    [...serverGroupManagers, ...loadBalancers, ...firewalls]
      .filter(
        (manifest: IServerGroupManager | ILoadBalancer | ISecurityGroup) =>
          manifest.cloudProvider === props.cloudProvider,
      )
      .forEach((manifest: IServerGroupManager | ILoadBalancer | ISecurityGroup) => {
        const [kind, name] = manifest.name.split(' ');
        manifests.push({
          account: manifest.account,
          location: manifest.region,
          name,
          kind,
          data: manifest,
          key: [manifest.account, manifest.region, manifest.name].join(':'),
        });
      });

    manifests = sortBy(
      manifests,
      (manifest) => {
        // TODO(dpeach): Could load an account here, then use the spinnakerKind -> kubernetesKind map to
        // construct a more maintainable ordering.
        const order = [
          // server group manager
          'deployment',
          // server group
          'replicaSet',
          'statefulSet',
          'daemonSet',
          'jobs',
          'cronJob',
          // load balancer
          'service',
          'ingress',
          // firewall
          'networkPolicy',
        ];
        return order.includes(manifest.kind) ? order.indexOf(manifest.kind) : Number.MAX_SAFE_INTEGER;
      },
      ['kind', 'name', 'location'],
    );

    return {
      selectedManifest: manifests.length ? manifests[0] : null,
      manifests,
    };
  };

  constructor(props: IManifestCopierProps) {
    super(props);
    this.state = ManifestCopier.getState(props);
  }

  public useManifest = (): void => {
    const {
      data: { account, region, name },
    } = this.state.selectedManifest;
    observableFrom(ManifestReader.getManifest(account, region, name))
      .pipe(takeUntil(this.destroy$))
      .subscribe((manifest: IManifest) => {
        this.props.onManifestSelected(JSON.parse(manifest.manifest.metadata.annotations[LAST_APPLIED_CONFIGURATION]));
      });
  };

  public manifestChanged = (option: Option) => {
    this.setState({ selectedManifest: option as IManifestOption });
  };

  public componentWillUnmount() {
    this.destroy$.next();
  }

  public render() {
    const { selectedManifest, manifests } = this.state;

    return (
      <Modal show={this.props.show} onHide={noop}>
        <ModalClose dismiss={this.props.onDismiss} />
        <div>
          <Modal.Header>
            <Modal.Title>Copy Manifest</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <form className="form-horizontal">
              <div className="form-group">
                <div className="col-md-4 col-md-offset-1 sm-label-left">
                  <b>Copy manifest from</b>
                </div>
              </div>
              <div className="form-group">
                <div className="col-md-10 col-md-offset-1">
                  <TetheredSelect
                    value={selectedManifest ? selectedManifest.key : null}
                    placeholder="Select..."
                    valueRenderer={this.manifestValueRenderer}
                    optionRenderer={this.manifestOptionRenderer}
                    options={manifests}
                    valueKey="key"
                    onChange={this.manifestChanged}
                    clearable={false}
                  />
                </div>
              </div>
            </form>
          </Modal.Body>
          <div className="modal-footer">
            {selectedManifest && (
              <button className="btn btn-primary" onClick={this.useManifest}>
                <span>Use this manifest</span>
                <span className="glyphicon glyphicon-chevron-right" />
              </button>
            )}
            {!selectedManifest && (
              <button className="btn" onClick={this.props.onDismiss}>
                <span>Cancel</span>
              </button>
            )}
          </div>
        </div>
      </Modal>
    );
  }

  private manifestValueRenderer = (option: Option) => {
    const kindLabel = robotToHuman(option.kind).trim();
    return (
      <span>
        <AccountTag account={option.account} />
        {option.data && <span> {option.name}</span>} ({kindLabel} in {option.location})
      </span>
    );
  };

  private manifestOptionRenderer = (option: Option) => {
    const kindLabel = robotToHuman(option.kind).trim();
    return (
      <h5>
        <AccountTag account={option.account} /> {option.name} ({kindLabel} in {option.location})
      </h5>
    );
  };
}
