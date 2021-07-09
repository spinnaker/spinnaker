import { get } from 'lodash';
import React from 'react';

import { AwsCodeBuildSecondarySourcesVersionList, AwsCodeBuildSourceList } from './AwsCodeBuildSourceList';
import { EXCLUDED_ARTIFACT_TYPES, IAwsCodeBuildSource, SOURCE_TYPES } from './IAwsCodeBuildSource';
import {
  FormikFormField,
  HelpField,
  IArtifact,
  IExpectedArtifact,
  IFormikStageConfigInjectedProps,
  IFormInputProps,
  IgorService,
  MapEditorInput,
  ReactSelectInput,
  StageArtifactSelector,
  TextInput,
  useData,
  YamlEditor,
} from '../../../../index';
import { CheckboxInput } from '../../../../presentation';

export function AwsCodeBuildStageForm(props: IFormikStageConfigInjectedProps) {
  const stage = props.formik.values;

  const { result: fetchAccountsResult, status: fetchAccountsStatus } = useData(
    () => IgorService.getCodeBuildAccounts(),
    [],
    [],
  );

  const { result: fetchProjectsResult, status: fetchProjectsStatus } = useData(
    () => IgorService.getCodeBuildProjects(stage.account),
    [],
    [stage.account],
  );

  const onFieldChange = (fieldName: string, fieldValue: any): void => {
    props.formik.setFieldValue(fieldName, fieldValue);
  };

  return (
    <div className="form-horizontal">
      <h4>Basic Settings</h4>
      <FormikFormField
        label="Account"
        name="account"
        input={(inputProps: IFormInputProps) => (
          <ReactSelectInput
            {...inputProps}
            clearable={false}
            isLoading={fetchAccountsStatus === 'PENDING'}
            stringOptions={fetchAccountsResult}
          />
        )}
      />
      <FormikFormField
        label="Project Name"
        name="projectName"
        input={(inputProps: IFormInputProps) => (
          <ReactSelectInput
            {...inputProps}
            clearable={false}
            isLoading={fetchProjectsStatus === 'PENDING'}
            stringOptions={fetchProjectsResult}
          />
        )}
      />
      <h4>Source Configuration</h4>
      <FormikFormField
        help={<HelpField id="pipeline.config.codebuild.source" />}
        label="Source"
        name="source.sourceOverride"
        input={(inputProps: IFormInputProps) => (
          <CheckboxInput {...inputProps} text="Override source to Spinnaker artifact" />
        )}
      />
      {get(stage, 'source.sourceOverride') === true && (
        <FormikFormField
          help={<HelpField id="pipeline.config.codebuild.sourceType" />}
          label="Source Type"
          name="source.sourceType"
          input={(inputProps: IFormInputProps) => (
            <ReactSelectInput {...inputProps} clearable={true} stringOptions={SOURCE_TYPES} />
          )}
        />
      )}
      {get(stage, 'source.sourceOverride') === true && (
        <FormikFormField
          label="Source Artifact Override"
          name="source"
          input={(inputProps: IFormInputProps) => (
            <StageArtifactSelector
              {...inputProps}
              artifact={get(stage, 'source.sourceArtifact.artifact')}
              excludedArtifactTypePatterns={EXCLUDED_ARTIFACT_TYPES}
              expectedArtifactId={get(stage, 'source.sourceArtifact.artifactId')}
              onArtifactEdited={(artifact: IArtifact) => {
                onFieldChange('source.sourceArtifact.artifact', artifact);
                onFieldChange('source.sourceArtifact.artifactId', null);
              }}
              onExpectedArtifactSelected={(artifact: IExpectedArtifact) => {
                onFieldChange('source.sourceArtifact.artifact', null);
                onFieldChange('source.sourceArtifact.artifactId', artifact.id);
              }}
              pipeline={props.pipeline}
              stage={stage}
            />
          )}
        />
      )}
      <FormikFormField
        help={<HelpField id="pipeline.config.codebuild.sourceVersion" />}
        label="Source Version"
        name="source.sourceVersion"
        input={(inputProps: IFormInputProps) => <TextInput {...inputProps} />}
      />
      <FormikFormField
        help={<HelpField id="pipeline.config.codebuild.buildspec" />}
        label="Buildspec"
        name="source.buildspec"
        input={(inputProps: IFormInputProps) => (
          <YamlEditor
            {...inputProps}
            value={get(stage, 'source.buildspec')}
            onChange={(buildspec: string, _: any) => onFieldChange('source.buildspec', buildspec)}
          />
        )}
      />
      <FormikFormField
        help={<HelpField id="pipeline.config.codebuild.secondarySources" />}
        label="Secondary Sources"
        name="secondarySources"
        input={(inputProps: IFormInputProps) => (
          <AwsCodeBuildSourceList
            {...inputProps}
            sources={get(stage, 'secondarySources')}
            updateSources={(sources: IAwsCodeBuildSource[]) => onFieldChange('secondarySources', sources)}
            stage={stage}
            pipeline={props.pipeline}
          />
        )}
      />
      <FormikFormField
        help={<HelpField id="pipeline.config.codebuild.secondarySourcesVersionOverride" />}
        label="Secondary Sources Version"
        name="secondarySourcesVersionOverride"
        input={(inputProps: IFormInputProps) => (
          <AwsCodeBuildSecondarySourcesVersionList {...inputProps} stage={stage} pipeline={props.pipeline} />
        )}
      />
      <h4>Environment Configuration</h4>
      <FormikFormField
        help={<HelpField id="pipeline.config.codebuild.image" />}
        label="Image"
        name="image"
        input={(inputProps: IFormInputProps) => <TextInput {...inputProps} />}
      />
      <h4>Advanced Configuration</h4>
      <FormikFormField
        help={<HelpField id="pipeline.config.codebuild.envVar" />}
        label="Environment Variables"
        name="environmentVariables"
        input={(inputProps: IFormInputProps) => (
          <MapEditorInput {...inputProps} addButtonLabel="Add environment variable" />
        )}
      />
    </div>
  );
}
