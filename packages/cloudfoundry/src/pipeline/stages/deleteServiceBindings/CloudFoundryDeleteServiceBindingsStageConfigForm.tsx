import React from 'react';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  AccountService,
  Application,
  IAccount,
  IFormikStageConfigInjectedProps,
  IRegion,
  NgReact,
  StageConfigField,
  StageConstants,
  TextInput,
} from '@spinnaker/core';

import { AccountRegionClusterSelector } from '../../../presentation/widgets/accountRegionClusterSelector';

interface ICloudfoundryDeleteServiceBindingsStageConfigState {
  accounts: IAccount[];
  regions: string[];
  application: Application;
}

export class CloudFoundryDeleteServiceBindingsStageConfigForm extends React.Component<
  IFormikStageConfigInjectedProps,
  ICloudfoundryDeleteServiceBindingsStageConfigState
> {
  private destroy$ = new Subject();

  constructor(props: IFormikStageConfigInjectedProps, context: any) {
    super(props, context);
    this.state = {
      accounts: [],
      regions: [],
      application: props.application,
    };
    observableFrom(AccountService.listAccounts('cloudfoundry'))
      .pipe(takeUntil(this.destroy$))
      .subscribe((rawAccounts: IAccount[]) => this.setState({ accounts: rawAccounts }));
  }

  public componentDidMount() {
    const stage = this.props.formik.values;
    this.props.formik.setFieldValue('cloudProvider', 'cloudfoundry');
    if (stage.serviceUnbindingRequests && stage.serviceUnbindingRequests.length === 0) {
      this.props.formik.setFieldValue('serviceUnbindingRequests', [
        {
          serviceInstanceName: '',
          updatable: false,
        },
      ]);
    }
    if (stage.credentials) {
      this.loadRegions(stage.credentials);
    }
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private loadRegions = (creds: string) => {
    this.setState({ regions: [] });
    observableFrom(AccountService.getRegionsForAccount(creds))
      .pipe(takeUntil(this.destroy$))
      .subscribe((regionList: IRegion[]) => {
        const regions = regionList.map((r) => r.name);
        regions.sort((a, b) => a.localeCompare(b));
        this.setState({ regions: regions });
      });
  };

  private addInputArtifact = () => {
    const stage = this.props.formik.values;
    const newServiceUnbindingRequests = [
      ...stage.serviceUnbindingRequests,
      {
        serviceInstanceName: '',
        updatable: false,
      },
    ];

    this.props.formik.setFieldValue('serviceUnbindingRequests', newServiceUnbindingRequests);
  };

  private removeInputArtifact = (index: number) => {
    const stage = this.props.formik.values;
    const newServiceUnbindingRequests = [...stage.serviceUnbindingRequests];
    newServiceUnbindingRequests.splice(index, 1);
    this.props.formik.setFieldValue('serviceUnbindingRequests', newServiceUnbindingRequests);
  };

  private targetUpdated = (target: string) => {
    this.props.formik.setFieldValue('target', target);
  };

  private accountRegionClusterUpdated = (stage: any): void => {
    this.props.formik.setFieldValue(`cluster`, stage.cluster);
    this.props.formik.setFieldValue(`credentials`, stage.credentials);
    this.props.formik.setFieldValue(`region`, stage.region);
  };

  public render() {
    const stage = this.props.formik.values;
    const { accounts, application } = this.state;
    const { target } = stage;
    const { TargetSelect } = NgReact;

    return (
      <>
        <h4>Basic Settings</h4>
        <div className="form-horizontal">
          <AccountRegionClusterSelector
            accounts={accounts}
            application={application}
            cloudProvider={'cloudfoundry'}
            isSingleRegion={true}
            onComponentUpdate={this.accountRegionClusterUpdated}
            component={stage}
          />
        </div>
        <StageConfigField label="Target">
          <TargetSelect model={{ target }} options={StageConstants.TARGET_LIST} onChange={this.targetUpdated} />
        </StageConfigField>
        <h4>Service Unbindings</h4>
        {stage.serviceUnbindingRequests && stage.serviceUnbindingRequests.length > 0 && (
          <div className="row form-group">
            {stage.serviceUnbindingRequests.map((a: any, index: number) => {
              return (
                <div key={index}>
                  <div className="col-md-offset-1 col-md-8">
                    <StageConfigField label="Service Instance Name">
                      <TextInput
                        onChange={(e: React.ChangeEvent<any>) => {
                          this.props.formik.setFieldValue(
                            `serviceUnbindingRequests[${index}].serviceInstanceName`,
                            e.target.value,
                          );
                        }}
                        value={a.serviceInstanceName}
                      />
                    </StageConfigField>
                  </div>
                  <div className="col-md-1">
                    <div className="form-control-static">
                      <button onClick={() => this.removeInputArtifact(index)}>
                        <span className="glyphicon glyphicon-trash" />
                        <span className="sr-only">Remove field</span>
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
        <StageConfigField fieldColumns={8} label={''}>
          <button className="btn btn-block btn-sm add-new" onClick={() => this.addInputArtifact()}>
            <span className="glyphicon glyphicon-plus-sign" />
            Add Service Unbinding
          </button>
        </StageConfigField>
      </>
    );
  }
}
