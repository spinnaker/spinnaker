import * as React from 'react';

import { Observable, Subject } from 'rxjs';

import {
  AccountService,
  Application,
  IAccount,
  IPipeline,
  IStageConfigProps,
  NgReact,
  StageConfigField,
  StageConstants,
} from '@spinnaker/core';

import { AccountRegionClusterSelector, Routes } from 'cloudfoundry/presentation';
import { Formik } from 'formik';

interface ICloudfoundryLoadBalancerStageConfigProps extends IStageConfigProps {
  pipeline: IPipeline;
}

interface ICloudFoundryMapLoadBalancersValues {
  routes: string[];
}

interface ICloudfoundryMapLoadBalancersStageConfigState {
  accounts: IAccount[];
  application: Application;
  initialValues: ICloudFoundryMapLoadBalancersValues;
  pipeline: IPipeline;
}

export class CloudfoundryMapLoadBalancersStageConfig extends React.Component<
  ICloudfoundryLoadBalancerStageConfigProps,
  ICloudfoundryMapLoadBalancersStageConfigState
> {
  private formikRef = React.createRef<Formik<ICloudFoundryMapLoadBalancersValues>>();
  private destroy$ = new Subject();

  constructor(props: ICloudfoundryLoadBalancerStageConfigProps) {
    super(props);
    const { loadBalancerNames } = props.stage;
    const routes = loadBalancerNames && loadBalancerNames.length ? loadBalancerNames : [''];
    this.props.updateStageField({
      cloudProvider: 'cloudfoundry',
      loadBalancerNames: routes,
    });
    this.state = {
      accounts: [],
      application: props.application,
      initialValues: {
        routes,
      },
      pipeline: props.pipeline,
    };
  }

  public componentDidMount(): void {
    Observable.fromPromise(AccountService.listAccounts('cloudfoundry'))
      .takeUntil(this.destroy$)
      .subscribe(accounts => this.setState({ accounts }));
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private targetUpdated = (target: string) => {
    this.props.updateStageField({ target });
  };

  private componentUpdated = (stage: any): void => {
    this.props.updateStageField({
      credentials: stage.credentials,
      region: stage.region,
      cluster: stage.cluster,
      loadBalancerNames: stage.loadBalancerNames,
    });
  };

  public render() {
    const { stage } = this.props;
    const { accounts, application, initialValues, pipeline } = this.state;
    const { target } = stage;
    const { TargetSelect } = NgReact;
    return (
      <div className="form-horizontal">
        {!pipeline.strategy && (
          <AccountRegionClusterSelector
            accounts={accounts}
            application={application}
            cloudProvider={'cloudfoundry'}
            isSingleRegion={true}
            onComponentUpdate={this.componentUpdated}
            component={stage}
          />
        )}
        <StageConfigField label="Target">
          <TargetSelect model={{ target }} options={StageConstants.TARGET_LIST} onChange={this.targetUpdated} />
        </StageConfigField>
        <Formik<ICloudFoundryMapLoadBalancersValues>
          ref={this.formikRef}
          initialValues={initialValues}
          onSubmit={null}
          render={() => {
            return (
              <Routes
                fieldName={'routes'}
                isRequired={true}
                singleRouteOnly={true}
                onChange={(routes: string[]) => {
                  stage.loadBalancerNames = routes;
                  this.componentUpdated(stage);
                }}
              />
            );
          }}
        />
      </div>
    );
  }
}
