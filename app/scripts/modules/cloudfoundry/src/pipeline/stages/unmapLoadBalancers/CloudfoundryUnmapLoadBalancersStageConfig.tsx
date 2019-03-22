import * as React from 'react';

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

interface ICloudFoundryUnmapLoadBalancersValues {
  routes: string[];
}

interface ICloudfoundryUnmapLoadBalancersStageConfigState {
  accounts: IAccount[];
  application: Application;
  initialValues: ICloudFoundryUnmapLoadBalancersValues;
  pipeline: IPipeline;
}

export class CloudfoundryUnmapLoadBalancersStageConfig extends React.Component<
  ICloudfoundryLoadBalancerStageConfigProps,
  ICloudfoundryUnmapLoadBalancersStageConfigState
> {
  private formikRef = React.createRef<Formik<ICloudFoundryUnmapLoadBalancersValues>>();

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

  public componentDidMount = () => {
    AccountService.listAccounts('cloudfoundry').then(accounts => {
      this.setState({ accounts });
    });
  };

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
        <Formik<ICloudFoundryUnmapLoadBalancersValues>
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
