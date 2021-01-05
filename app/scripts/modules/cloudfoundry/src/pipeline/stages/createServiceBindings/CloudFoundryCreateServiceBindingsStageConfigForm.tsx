import React from 'react';
import { Observable, Subject } from 'rxjs';

import {
  StageConfigField,
  IArtifact,
  excludeAllTypesExcept,
  ArtifactTypePatterns,
  StageArtifactSelectorDelegate,
  IFormikStageConfigInjectedProps,
  TextInput,
  AccountService,
  IAccount,
  IRegion,
  NgReact,
  Application,
  StageConstants,
} from '@spinnaker/core';

import { AccountRegionClusterSelector } from '../../../presentation/widgets/accountRegionClusterSelector';

interface ICloudfoundryCreateServiceBindingsStageConfigState {
  accounts: IAccount[];
  regions: string[];
  application: Application;
}

export class CloudFoundryCreateServiceBindingsStageConfigForm extends React.Component<
  IFormikStageConfigInjectedProps,
  ICloudfoundryCreateServiceBindingsStageConfigState
> {
  private static readonly excludedArtifactTypes = excludeAllTypesExcept(
    ArtifactTypePatterns.BITBUCKET_FILE,
    ArtifactTypePatterns.CUSTOM_OBJECT,
    ArtifactTypePatterns.EMBEDDED_BASE64,
    ArtifactTypePatterns.GCS_OBJECT,
    ArtifactTypePatterns.GITHUB_FILE,
    ArtifactTypePatterns.GITLAB_FILE,
    ArtifactTypePatterns.S3_OBJECT,
    ArtifactTypePatterns.HTTP_FILE,
  );

  private destroy$ = new Subject();

  constructor(props: IFormikStageConfigInjectedProps, context: any) {
    super(props, context);
    this.state = {
      accounts: [],
      regions: [],
      application: props.application,
    };
    Observable.fromPromise(AccountService.listAccounts('cloudfoundry'))
      .takeUntil(this.destroy$)
      .subscribe((rawAccounts: IAccount[]) => this.setState({ accounts: rawAccounts }));
  }

  public componentDidMount() {
    const stage = this.props.formik.values;
    this.props.formik.setFieldValue('cloudProvider', 'cloudfoundry');
    if (stage.serviceBindingRequests && stage.serviceBindingRequests.length === 0) {
      this.props.formik.setFieldValue('serviceBindingRequests', [
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
    Observable.fromPromise(AccountService.getRegionsForAccount(creds))
      .takeUntil(this.destroy$)
      .subscribe((regionList: IRegion[]) => {
        const regions = regionList.map((r) => r.name);
        regions.sort((a, b) => a.localeCompare(b));
        this.setState({ regions: regions });
      });
  };

  private onTemplateArtifactEdited = (artifact: IArtifact, index: number) => {
    this.props.formik.setFieldValue(`serviceBindingRequests[${index}].id`, null);
    this.props.formik.setFieldValue(`serviceBindingRequests[${index}].artifact`, artifact);
  };

  private onTemplateArtifactSelected = (id: string, index: number) => {
    this.props.formik.setFieldValue(`serviceBindingRequests[${index}].id`, id);
    this.props.formik.setFieldValue(`serviceBindingRequests[${index}].artifact`, null);
  };

  private addInputArtifact = () => {
    const stage = this.props.formik.values;
    const newServiceBindingRequests = [
      ...stage.serviceBindingRequests,
      {
        serviceInstanceName: '',
        updatable: false,
      },
    ];

    this.props.formik.setFieldValue('serviceBindingRequests', newServiceBindingRequests);
  };

  private removeInputArtifact = (index: number) => {
    const stage = this.props.formik.values;
    const newServiceBindingRequests = [...stage.serviceBindingRequests];
    newServiceBindingRequests.splice(index, 1);
    this.props.formik.setFieldValue('serviceBindingRequests', newServiceBindingRequests);
  };

  private targetUpdated = (target: string) => {
    this.props.formik.setFieldValue('target', target);
  };

  private restageRequiredUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.formik.setFieldValue('restageRequired', event.target.checked);
    this.props.formik.setFieldValue('restartRequired', !event.target.checked);
  };

  private restartRequiredUpdated = (event: React.ChangeEvent<HTMLInputElement>) => {
    this.props.formik.setFieldValue('restartRequired', event.target.checked);
    this.props.formik.setFieldValue('restageRequired', !event.target.checked);
  };

  private updatableBindingUpdated = (event: React.ChangeEvent<HTMLInputElement>, index: number) => {
    this.props.formik.setFieldValue(`serviceBindingRequests[${index}].updatable`, event.target.checked);
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
        <StageConfigField label="Restage Required">
          <input type="checkbox" checked={stage.restageRequired} onChange={this.restageRequiredUpdated} />
        </StageConfigField>
        <StageConfigField label="Restart Required">
          <input type="checkbox" checked={stage.restartRequired} onChange={this.restartRequiredUpdated} />
        </StageConfigField>
        <h4>Service Bindings</h4>
        {stage.serviceBindingRequests && stage.serviceBindingRequests.length > 0 && (
          <div className="row form-group">
            {stage.serviceBindingRequests.map((a: any, index: number) => {
              return (
                <div key={index}>
                  <div className="col-md-offset-1 col-md-8">
                    <StageConfigField label="Service Instance Name">
                      <TextInput
                        onChange={(e: React.ChangeEvent<any>) => {
                          this.props.formik.setFieldValue(
                            `serviceBindingRequests[${index}].serviceInstanceName`,
                            e.target.value,
                          );
                        }}
                        value={a.serviceInstanceName}
                      />
                    </StageConfigField>
                    <StageArtifactSelectorDelegate
                      artifact={a.artifact}
                      excludedArtifactTypePatterns={
                        CloudFoundryCreateServiceBindingsStageConfigForm.excludedArtifactTypes
                      }
                      expectedArtifactId={a.id}
                      label="Service Binding Parameters"
                      onArtifactEdited={(artifact) => {
                        this.onTemplateArtifactEdited(artifact, index);
                      }}
                      onExpectedArtifactSelected={(artifact) => this.onTemplateArtifactSelected(artifact.id, index)}
                      pipeline={this.props.pipeline}
                      stage={stage}
                    />
                    <StageConfigField label="Updatable Binding">
                      <input
                        type="checkbox"
                        checked={a.updatable}
                        onChange={(event) => this.updatableBindingUpdated(event, index)}
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
            Add Service Binding
          </button>
        </StageConfigField>
      </>
    );
  }
}
