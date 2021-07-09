import { IDeferred } from 'angular';
import { IModalServiceInstance } from 'angular-ui-bootstrap';
import { Form, Formik } from 'formik';
import { $q } from 'ngimport';
import React from 'react';
import { Modal, ModalFooter } from 'react-bootstrap';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  AccountService,
  Application,
  IAccount,
  ILoadBalancerModalProps,
  IModalComponentProps,
  IServerGroup,
  ModalClose,
  noop,
  ReactInjector,
  ReactModal,
  SpinFormik,
  TaskMonitor,
  TaskMonitorWrapper,
} from '@spinnaker/core';
import { ICloudFoundryServerGroup } from '../../domain';
import { AccountRegionClusterSelector, Routes } from '../../presentation';

export interface ICreateCloudFoundryMapLoadBalancerState {
  accounts: IAccount[];
  regions: string[];
  application: Application;
  selectedValues: ICloudFoundryLoadBalancerModalValues;
  taskMonitor: TaskMonitor;
}

export interface ICloudFoundryLoadBalancerModalProps extends IModalComponentProps {
  application: Application;
}

export interface ICloudFoundryLoadBalancerModalValues {
  credentials: string;
  region: string;
  cluster: string;
  routes: string[];
}

export class CloudFoundryMapLoadBalancerModal extends React.Component<
  ICloudFoundryLoadBalancerModalProps,
  ICreateCloudFoundryMapLoadBalancerState
> {
  public static defaultProps: Partial<ICloudFoundryLoadBalancerModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private destroy$ = new Subject();
  private formikRef = React.createRef<Formik<ICloudFoundryLoadBalancerModalValues>>();
  private $uibModalInstanceEmulation: IModalServiceInstance & { deferred?: IDeferred<any> };

  constructor(props: ICloudFoundryLoadBalancerModalProps) {
    super(props);

    const deferred = $q.defer();
    const promise = deferred.promise;
    this.$uibModalInstanceEmulation = {
      result: promise,
      close: () => this.props.dismissModal(),
      dismiss: () => this.props.dismissModal(),
    } as IModalServiceInstance;
    Object.assign(this.$uibModalInstanceEmulation, { deferred });
    this.state = {
      accounts: [],
      regions: [],
      application: props.application,
      selectedValues: {
        credentials: '',
        region: '',
        cluster: '',
        routes: [''],
      },
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: 'Mapping a route to your server group',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: () => this.props.application.serverGroups.refresh(),
      }),
    };
    observableFrom(AccountService.listAccounts('cloudfoundry'))
      .pipe(takeUntil(this.destroy$))
      .subscribe((rawAccounts: IAccount[]) => this.setState({ accounts: rawAccounts }));
  }

  public static show(props: ILoadBalancerModalProps): Promise<void> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    return ReactModal.show(
      CloudFoundryMapLoadBalancerModal,
      {
        application: props.app,
        // className: 'create-pipeline-modal-overflow-visible',
      },
      modalProps,
    );
  }

  public componentDidMount(): void {
    observableFrom(AccountService.listAccounts('cloudfoundry'))
      .pipe(takeUntil(this.destroy$))
      .subscribe((accounts) => this.setState({ accounts }));
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private submit = (values: ICloudFoundryLoadBalancerModalValues): void => {
    const { routes } = values;
    const serverGroup: ICloudFoundryServerGroup = this.props.application.serverGroups?.data?.find(
      (sg: ICloudFoundryServerGroup) =>
        sg.cluster === this.state.selectedValues.cluster &&
        sg.account === this.state.selectedValues.credentials &&
        sg.region === this.state.selectedValues.region,
    );

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
      return ReactInjector.serverGroupWriter.mapLoadBalancers(coreServerGroup, this.props.application, {
        serverGroupName: serverGroup.name,
      });
    });
  };

  private close = (args?: any): void => {
    this.props.dismissModal.apply(null, args);
  };

  private accountRegionClusterUpdated = (values: any) => {
    this.setState({
      selectedValues: {
        cluster: values.cluster,
        credentials: values.credentials,
        region: values.region,
        routes: [''],
      },
    });
  };

  public render() {
    const { accounts, application, selectedValues } = this.state;
    return (
      <>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <SpinFormik<ICloudFoundryLoadBalancerModalValues>
          ref={this.formikRef}
          initialValues={selectedValues}
          onSubmit={this.submit}
          render={(formik) => {
            return (
              <>
                <ModalClose dismiss={this.close} />
                <Modal.Header>
                  <Modal.Title>Map route</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                  <Form className="form-horizontal">
                    <AccountRegionClusterSelector
                      accounts={accounts}
                      application={application}
                      cloudProvider={'cloudfoundry'}
                      isSingleRegion={true}
                      onComponentUpdate={this.accountRegionClusterUpdated}
                      component={selectedValues}
                    />
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
