import React from 'react';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  AccountService,
  ArtifactTypePatterns,
  excludeAllTypesExcept,
  IAccount,
  IArtifact,
  IExpectedArtifact,
  IFormikStageConfigInjectedProps,
  StageArtifactSelectorDelegate,
} from '@spinnaker/core';
import { FormikAccountRegionSelector } from '../../../common/FormikAccountRegionSelector';

export interface IAppEngineDeployConfigSettingsState {
  accounts: IAccount[];
}

export class DeployAppengineConfigForm extends React.Component<
  IFormikStageConfigInjectedProps,
  IAppEngineDeployConfigSettingsState
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
  public state: IAppEngineDeployConfigSettingsState = {
    accounts: [],
  };

  public componentDidMount() {
    observableFrom(AccountService.listAccounts('appengine'))
      .pipe(takeUntil(this.destroy$))
      .subscribe((accounts) => this.setState({ accounts }));
  }

  private onTemplateArtifactEdited = (artifact: IArtifact, name: string) => {
    this.props.formik.setFieldValue(`${name}.id`, null);
    this.props.formik.setFieldValue(`${name}.artifact`, artifact);
    this.props.formik.setFieldValue(`${name}.account`, artifact.artifactAccount);
  };

  private onTemplateArtifactSelected = (id: string, name: string) => {
    this.props.formik.setFieldValue(`${name}.id`, id);
    this.props.formik.setFieldValue(`${name}.artifact`, null);
  };

  private removeInputArtifact = (name: string) => {
    this.props.formik.setFieldValue(name, null);
  };

  private getInputArtifact = (stage: any, name: string) => {
    if (!stage[name]) {
      return {
        account: '',
        id: '',
      };
    } else {
      return stage[name];
    }
  };

  public render() {
    const stage = this.props.formik.values;
    const accounts = this.state.accounts;
    return (
      <div>
        <div className="col-md-offset-0 col-md-9">
          <h4>Basic Settings</h4>
        </div>
        <div>
          <FormikAccountRegionSelector
            componentName={''}
            accounts={accounts}
            application={this.props.application}
            cloudProvider={'appengine'}
            credentialsField={'account'}
            formik={this.props.formik}
          />
        </div>
        <div className="col-md-offset-0 col-md-9">
          <h4>Configuration Settings</h4>
        </div>
        <div>
          <div className="col-md-offset-1 col-md-9">
            <StageArtifactSelectorDelegate
              artifact={this.getInputArtifact(stage, 'cronArtifact').artifact}
              excludedArtifactTypePatterns={DeployAppengineConfigForm.excludedArtifactTypes}
              expectedArtifactId={this.getInputArtifact(stage, 'cronArtifact').id}
              label="Cron Artifact"
              onArtifactEdited={(artifact) => {
                this.onTemplateArtifactEdited(artifact, 'cronArtifact');
              }}
              helpKey={''}
              onExpectedArtifactSelected={(artifact: IExpectedArtifact) =>
                this.onTemplateArtifactSelected(artifact.id, 'cronArtifact')
              }
              pipeline={this.props.pipeline}
              stage={stage}
            />
          </div>
          <div className="col-md-1">
            <div className="form-control-static">
              <button onClick={() => this.removeInputArtifact('cronArtifact')}>
                <span className="glyphicon glyphicon-trash" />
                <span className="sr-only">Remove field</span>
              </button>
            </div>
          </div>
        </div>
        <div>
          <div className="col-md-offset-1 col-md-9">
            <StageArtifactSelectorDelegate
              artifact={this.getInputArtifact(stage, 'dispatchArtifact').artifact}
              excludedArtifactTypePatterns={DeployAppengineConfigForm.excludedArtifactTypes}
              expectedArtifactId={this.getInputArtifact(stage, 'dispatchArtifact').id}
              label="Dispatch Artifact"
              onArtifactEdited={(artifact) => {
                this.onTemplateArtifactEdited(artifact, 'dispatchArtifact');
              }}
              helpKey={''}
              onExpectedArtifactSelected={(artifact: IExpectedArtifact) =>
                this.onTemplateArtifactSelected(artifact.id, 'dispatchArtifact')
              }
              pipeline={this.props.pipeline}
              stage={stage}
            />
          </div>
          <div className="col-md-1">
            <div className="form-control-static">
              <button onClick={() => this.removeInputArtifact('dispatchArtifact')}>
                <span className="glyphicon glyphicon-trash" />
                <span className="sr-only">Remove field</span>
              </button>
            </div>
          </div>
        </div>
        <div>
          <div className="col-md-offset-1 col-md-9">
            <StageArtifactSelectorDelegate
              artifact={this.getInputArtifact(stage, 'indexArtifact').artifact}
              excludedArtifactTypePatterns={DeployAppengineConfigForm.excludedArtifactTypes}
              expectedArtifactId={this.getInputArtifact(stage, 'indexArtifact').id}
              label="Index Artifact"
              onArtifactEdited={(artifact) => {
                this.onTemplateArtifactEdited(artifact, 'indexArtifact');
              }}
              helpKey={''}
              onExpectedArtifactSelected={(artifact: IExpectedArtifact) =>
                this.onTemplateArtifactSelected(artifact.id, 'indexArtifact')
              }
              pipeline={this.props.pipeline}
              stage={stage}
            />
          </div>
          <div className="col-md-1">
            <div className="form-control-static">
              <button onClick={() => this.removeInputArtifact('indexArtifact')}>
                <span className="glyphicon glyphicon-trash" />
                <span className="sr-only">Remove field</span>
              </button>
            </div>
          </div>
        </div>
        <div>
          <div className="col-md-offset-1 col-md-9">
            <StageArtifactSelectorDelegate
              artifact={this.getInputArtifact(stage, 'queueArtifact').artifact}
              excludedArtifactTypePatterns={DeployAppengineConfigForm.excludedArtifactTypes}
              expectedArtifactId={this.getInputArtifact(stage, 'queueArtifact').id}
              label="Queue Artifact"
              onArtifactEdited={(artifact) => {
                this.onTemplateArtifactEdited(artifact, 'queueArtifact');
              }}
              helpKey={''}
              onExpectedArtifactSelected={(artifact: IExpectedArtifact) =>
                this.onTemplateArtifactSelected(artifact.id, 'queueArtifact')
              }
              pipeline={this.props.pipeline}
              stage={stage}
            />
          </div>
          <div className="col-md-1">
            <div className="form-control-static">
              <button onClick={() => this.removeInputArtifact('queueArtifact')}>
                <span className="glyphicon glyphicon-trash" />
                <span className="sr-only">Remove field</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }
}
