import { Form, Formik } from 'formik';
import React from 'react';
import { Modal, ModalFooter } from 'react-bootstrap';

import {
  Application,
  IModalComponentProps,
  IServerGroup,
  IServerGroupJob,
  ModalClose,
  noop,
  ReactInjector,
  ReactModal,
  SpinFormik,
  TaskMonitor,
  TaskMonitorWrapper,
} from '@spinnaker/core';
import { ICloudFoundryServerGroup } from '../../../domain';
import { Routes } from '../../../presentation/forms/serverGroup';

export interface ICloudFoundryLoadBalancerLinksModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: ICloudFoundryServerGroup;
}

export interface ICloudFoundryLoadBalancerLinksModalState {
  initialValues: ICloudFoundryLoadBalancerLinksModalValues;
  taskMonitor: TaskMonitor;
}

export interface ICloudFoundryLoadBalancerLinksModalValues {
  routes: string[];
}

export interface ICloudFoundryLoadBalancerLinkJob extends IServerGroupJob {
  routes: string[];
  serverGroupName: string;
}

export class CloudFoundryUnmapLoadBalancersModal extends React.Component<
  ICloudFoundryLoadBalancerLinksModalProps,
  ICloudFoundryLoadBalancerLinksModalState
> {
  public static defaultProps: Partial<ICloudFoundryLoadBalancerLinksModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private formikRef = React.createRef<Formik<ICloudFoundryLoadBalancerLinksModalValues>>();

  public static show(props: ICloudFoundryLoadBalancerLinksModalProps): Promise<ICloudFoundryLoadBalancerLinkJob> {
    return ReactModal.show(CloudFoundryUnmapLoadBalancersModal, props, {});
  }

  constructor(props: ICloudFoundryLoadBalancerLinksModalProps) {
    super(props);

    this.state = {
      initialValues: {
        routes: [''],
      },
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: 'Unmapping a route from your server group',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: () => this.props.application.serverGroups.refresh(),
      }),
    };
  }

  private close = (args?: any): void => {
    this.props.dismissModal.apply(null, args);
  };

  private submit = (values: ICloudFoundryLoadBalancerLinksModalValues): void => {
    const { routes } = values;
    const { serverGroup } = this.props;
    const coreServerGroup: IServerGroup = {
      account: serverGroup.account,
      cloudProvider: serverGroup.cloudProvider,
      cluster: serverGroup.cluster,
      instanceCounts: serverGroup.instanceCounts,
      instances: serverGroup.instances,
      loadBalancers: routes,
      name: serverGroup.name,
      region: serverGroup.region,
      type: serverGroup.type,
    };

    this.state.taskMonitor.submit(() => {
      return ReactInjector.serverGroupWriter.unmapLoadBalancers(coreServerGroup, this.props.application, {
        serverGroupName: serverGroup.name,
      });
    });
  };

  public render() {
    const { serverGroup } = this.props;
    const { initialValues } = this.state;
    return (
      <>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <SpinFormik<ICloudFoundryLoadBalancerLinksModalValues>
          ref={this.formikRef}
          initialValues={initialValues}
          onSubmit={this.submit}
          render={(formik) => {
            return (
              <>
                <ModalClose dismiss={this.close} />
                <Modal.Header>
                  <Modal.Title>Unmap route from {serverGroup.name}</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                  <Form className="form-horizontal">
                    <Routes fieldName="routes" isRequired={true} singleRouteOnly={true} />
                  </Form>
                </Modal.Body>
                <ModalFooter>
                  <button className="btn btn-default" onClick={this.close}>
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn btn-primary"
                    onClick={() => this.submit(formik.values)}
                    disabled={!formik.isValid}
                  >
                    Submit
                  </button>
                </ModalFooter>
              </>
            );
          }}
        />
      </>
    );
  }
}
