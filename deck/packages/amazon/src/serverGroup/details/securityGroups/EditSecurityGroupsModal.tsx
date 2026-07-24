import React from 'react';
import { Modal } from 'react-bootstrap';
import Select from 'react-select';

import type { Application, DeckRuntimeServices, IModalComponentProps, ISecurityGroup } from '@spinnaker/core';
import {
  confirmNotManaged,
  DeckRuntimeContext,
  FirewallLabels,
  ModalClose,
  ReactModal,
  TaskMonitor,
  TaskMonitorWrapper,
} from '@spinnaker/core';

import { AwsModalFooter } from '../../../common/AwsModalFooter';
import type { IAmazonServerGroup } from '../../../domain';

export interface IEditSecurityGroupsModalProps extends IModalComponentProps {
  application: Application;
  securityGroups?: ISecurityGroup[];
  serverGroup: IAmazonServerGroup;
}

interface IEditSecurityGroupsModalState {
  availableSecurityGroups: ISecurityGroup[];
  securityGroups: ISecurityGroup[];
  securityGroupsLoadError?: string;
  securityGroupsLoaded: boolean;
  taskMonitor: TaskMonitor;
}

export function isLaunchTemplateBacked(serverGroup: IAmazonServerGroup): boolean {
  return Boolean(serverGroup.launchTemplate || serverGroup.mixedInstancesPolicy);
}

export function filterSecurityGroupsForServerGroup(
  allGroups: any,
  serverGroup: IAmazonServerGroup,
  attached: ISecurityGroup[],
): ISecurityGroup[] {
  const regionalGroups: ISecurityGroup[] = allGroups?.[serverGroup.account]?.aws?.[serverGroup.region] || [];
  const matchingGroups = regionalGroups.filter((group) => group.vpcId === serverGroup.vpcId);
  const matchingById = new Map(matchingGroups.map((group) => [group.id, group]));
  const selectedIds = new Set<string>();
  const selected = (attached || []).map((group) => {
    selectedIds.add(group.id);
    return matchingById.get(group.id) || group;
  });
  const available = matchingGroups
    .filter((group) => !selectedIds.has(group.id))
    .sort((left, right) => left.name.localeCompare(right.name));
  return [...selected, ...available];
}

export class EditSecurityGroupsModal extends React.Component<
  IEditSecurityGroupsModalProps,
  IEditSecurityGroupsModalState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  public static show(props: IEditSecurityGroupsModalProps, runtimeServices: DeckRuntimeServices) {
    return confirmNotManaged(props.serverGroup, props.application).then(
      (notManaged) => notManaged && ReactModal.show(EditSecurityGroupsModal, props, undefined, runtimeServices),
    );
  }

  private initialSecurityGroups =
    this.props.securityGroups ||
    (this.props.serverGroup.securityGroups || []).map((id: string) => ({ id, name: id } as ISecurityGroup));

  public state: IEditSecurityGroupsModalState = {
    availableSecurityGroups: this.initialSecurityGroups,
    securityGroups: this.initialSecurityGroups,
    securityGroupsLoaded: false,
    taskMonitor: new TaskMonitor({
      application: this.props.application,
      title: `Update ${FirewallLabels.get('Firewalls')} for ${this.props.serverGroup.name}`,
      modalInstance: TaskMonitor.modalInstanceEmulation(this.props.closeModal, this.props.dismissModal),
      onTaskComplete: () => this.props.application.serverGroups.refresh(),
    }),
  };

  private isUnmounted = false;

  public componentDidMount() {
    this.loadSecurityGroups();
  }

  public componentWillUnmount() {
    this.isUnmounted = true;
  }

  private loadSecurityGroups = () => {
    this.setState({ securityGroupsLoaded: false, securityGroupsLoadError: undefined });
    return this.context.services.securityGroupReader
      .getAllSecurityGroups()
      .then((allGroups) => {
        if (!this.isUnmounted) {
          this.setState({
            availableSecurityGroups: filterSecurityGroupsForServerGroup(
              allGroups,
              this.props.serverGroup,
              this.state.securityGroups,
            ),
            securityGroupsLoaded: true,
          });
        }
      })
      .catch(() => {
        if (!this.isUnmounted) {
          this.setState({
            securityGroupsLoaded: true,
            securityGroupsLoadError: 'Unable to load security groups. Check your connection and try again.',
          });
        }
      });
  };

  private submit = () => {
    const { application, serverGroup } = this.props;
    this.state.taskMonitor.submit(() =>
      this.context.services.serverGroupWriter.updateSecurityGroups(
        serverGroup,
        this.state.securityGroups,
        application,
        isLaunchTemplateBacked(serverGroup),
      ),
    );
  };

  public render() {
    const { dismissModal, serverGroup } = this.props;
    return (
      <>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <ModalClose dismiss={dismissModal} />
        <Modal.Header>
          <Modal.Title>
            Edit {FirewallLabels.get('Firewalls')} for {serverGroup.name}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body className="container-fluid form-horizontal">
          <div className="form-group">
            <div className="col-md-10 col-md-offset-1">
              <Select
                labelKey="name"
                isLoading={!this.state.securityGroupsLoaded}
                multi={true}
                onChange={(securityGroups: ISecurityGroup[]) => this.setState({ securityGroups: securityGroups || [] })}
                optionRenderer={(group: ISecurityGroup) => <span>{`${group.name} (${group.id})`}</span>}
                options={this.state.availableSecurityGroups}
                value={this.state.securityGroups}
                valueKey="id"
              />
              {this.state.securityGroupsLoadError && (
                <div className="alert alert-danger security-groups-load-error" role="alert">
                  {this.state.securityGroupsLoadError}{' '}
                  <button className="btn btn-default btn-xs" onClick={this.loadSecurityGroups} type="button">
                    Retry
                  </button>
                </div>
              )}
            </div>
          </div>
        </Modal.Body>
        <AwsModalFooter
          account={serverGroup.account}
          isValid={this.state.securityGroupsLoaded && !this.state.securityGroupsLoadError}
          onCancel={dismissModal}
          onSubmit={this.submit}
        />
      </>
    );
  }
}
