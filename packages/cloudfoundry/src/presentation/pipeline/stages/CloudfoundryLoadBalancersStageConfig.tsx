import { Formik } from 'formik';
import React from 'react';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  AccountService,
  Application,
  IAccount,
  IPipeline,
  IStageConfigProps,
  NgReact,
  SpinFormik,
  StageConfigField,
  StageConstants,
} from '@spinnaker/core';

import { Routes } from '../../forms/serverGroup';
import { AccountRegionClusterSelector } from '../../widgets/accountRegionClusterSelector';

interface ICloudfoundryLoadBalancerStageConfigProps extends IStageConfigProps {
  pipeline: IPipeline;
}

interface ICloudFoundryLoadBalancersValues {
  routes: string[];
}

interface ICloudfoundryLoadBalancersStageConfigState {
  accounts: IAccount[];
  application: Application;
  initialValues: ICloudFoundryLoadBalancersValues;
  pipeline: IPipeline;
}

export class CloudfoundryLoadBalancersStageConfig extends React.Component<
  ICloudfoundryLoadBalancerStageConfigProps,
  ICloudfoundryLoadBalancersStageConfigState
> {
  private destroy$ = new Subject();
  private formikRef = React.createRef<Formik<ICloudFoundryLoadBalancersValues>>();

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
    observableFrom(AccountService.listAccounts('cloudfoundry'))
      .pipe(takeUntil(this.destroy$))
      .subscribe((accounts) => this.setState({ accounts }));
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
        <SpinFormik<ICloudFoundryLoadBalancersValues>
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
