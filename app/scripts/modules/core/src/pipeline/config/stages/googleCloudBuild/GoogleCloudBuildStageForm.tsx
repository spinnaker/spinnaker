import React from 'react';
import { Option } from 'react-select';
import { get } from 'lodash';

import {
  ArtifactTypePatterns,
  excludeAllTypesExcept,
  FormikFormField,
  FormikSpelContextProvider,
  IArtifact,
  IExpectedArtifact,
  IFormInputProps,
  IFormikStageConfigInjectedProps,
  IgorService,
  IPipeline,
  RadioButtonInput,
  ReactSelectInput,
  SpelService,
  StageArtifactSelectorDelegate,
  TextInput,
  useData,
  yamlDocumentsToString,
  YamlEditor,
} from 'core';

import { BuildDefinitionSource, TriggerType } from './IGoogleCloudBuildStage';

interface IGoogleCloudBuildStageFormProps {
  updatePipeline: (pipeline: IPipeline) => void;
}

const SOURCE_OPTIONS: Array<Option<string>> = [
  { value: BuildDefinitionSource.TEXT, label: 'Text' },
  { value: BuildDefinitionSource.ARTIFACT, label: 'Artifact' },
  { value: BuildDefinitionSource.TRIGGER, label: 'Trigger' },
];

const TRIGGER_TYPE_OPTIONS: Array<Option<string>> = [
  { value: TriggerType.BRANCH, label: 'Branch name' },
  { value: TriggerType.TAG, label: 'Tag Name' },
  { value: TriggerType.COMMIT, label: 'Commit SHA' },
];

const EXCLUDED_ARTIFACT_TYPES: RegExp[] = excludeAllTypesExcept(
  ArtifactTypePatterns.BITBUCKET_FILE,
  ArtifactTypePatterns.CUSTOM_OBJECT,
  ArtifactTypePatterns.GCS_OBJECT,
  ArtifactTypePatterns.GITHUB_FILE,
  ArtifactTypePatterns.GITLAB_FILE,
  ArtifactTypePatterns.HTTP_FILE,
  ArtifactTypePatterns.S3_OBJECT,
);

export function GoogleCloudBuildStageForm(props: IGoogleCloudBuildStageFormProps & IFormikStageConfigInjectedProps) {
  const stage = props.formik.values;

  const [rawBuildDefinitionYaml, setRawBuildDefinitionYaml] = React.useState(() =>
    stage.buildDefinition ? yamlDocumentsToString([stage.buildDefinition]) : '',
  );

  const { result: fetchAccountsResult, status: fetchAccountsStatus } = useData(
    () => IgorService.getGcbAccounts(),
    [],
    [],
  );

  const { result: fetchTriggersResult, status: fetchTriggersStatus } = useData(
    () => {
      if (SpelService.includesSpel(stage.account)) {
        return null;
      }
      return IgorService.getGcbTriggers(stage.account);
    },
    [],
    [stage.account],
  );

  const onYamlChange = (rawYaml: string, yamlDocs: any): void => {
    setRawBuildDefinitionYaml(rawYaml);
    const buildDefinition = Array.isArray(yamlDocs) && yamlDocs.length > 0 ? yamlDocs[0] : null;
    props.formik.setFieldValue('buildDefinition', buildDefinition);
  };

  const setArtifactId = (expectedArtifactId: string): void => {
    props.formik.setFieldValue('buildDefinitionArtifact.artifactId', expectedArtifactId);
    props.formik.setFieldValue('buildDefinitionArtifact.artifact', null);
  };

  const setArtifact = (artifact: IArtifact): void => {
    props.formik.setFieldValue('buildDefinitionArtifact.artifact', artifact);
    props.formik.setFieldValue('buildDefinitionArtifact.artifactId', null);
  };

  const setArtifactAccount = (accountName: string): void => {
    props.formik.setFieldValue('buildDefinitionArtifact.artifactAccount', accountName);
  };

  // When build definition source changes, clear any no-longer-relevant fields.
  React.useEffect(() => {
    if (stage.buildDefinitionSource !== BuildDefinitionSource.TEXT && stage.buildDefinition) {
      props.formik.setFieldValue('buildDefinition', null);
      setRawBuildDefinitionYaml('');
    }
    if (stage.buildDefinitionSource !== BuildDefinitionSource.ARTIFACT && stage.buildDefinitionArtifact) {
      props.formik.setFieldValue('buildDefinitionArtifact', null);
    }
    if (stage.buildDefinitionSource !== BuildDefinitionSource.TRIGGER && stage.repoSource) {
      props.formik.setFieldValue('repoSource', null);
    }
    if (stage.buildDefinitionSource !== BuildDefinitionSource.TRIGGER && stage.triggerId) {
      props.formik.setFieldValue('triggerId', null);
    }
    if (stage.buildDefinitionSource !== BuildDefinitionSource.TRIGGER && stage.triggerType) {
      props.formik.setFieldValue('triggerType', null);
    }
  }, [stage.buildDefinitionSource]);

  // When trigger type changes, clear any no-longer-relevant fields.
  React.useEffect(() => {
    if (stage.buildDefinitionSource !== BuildDefinitionSource.TRIGGER) {
      return;
    }
    const branchKey = `repoSource.${TriggerType.BRANCH}`;
    if (stage.triggerType !== TriggerType.BRANCH && get(stage, branchKey)) {
      props.formik.setFieldValue(branchKey, null);
    }
    const commitKey = `repoSource.${TriggerType.COMMIT}`;
    if (stage.triggerType !== TriggerType.COMMIT && get(stage, commitKey)) {
      props.formik.setFieldValue(commitKey, null);
    }
    const tagKey = `repoSource.${TriggerType.TAG}`;
    if (stage.triggerType !== TriggerType.TAG && get(stage, tagKey)) {
      props.formik.setFieldValue(tagKey, null);
    }
  }, [stage.triggerType]);

  return (
    <FormikSpelContextProvider value={true}>
      <div className="form-horizontal">
        <FormikFormField
          fastField={false}
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
          fastField={false}
          label="Build Definition Source"
          name="buildDefinitionSource"
          input={(inputProps: IFormInputProps) => <RadioButtonInput {...inputProps} options={SOURCE_OPTIONS} />}
          spelAware={false}
        />
        {stage.buildDefinitionSource === BuildDefinitionSource.TEXT && (
          <FormikFormField
            fastField={false}
            label="Build Definition"
            name="buildDefinition"
            input={(inputProps: IFormInputProps) => (
              <YamlEditor {...inputProps} value={rawBuildDefinitionYaml} onChange={onYamlChange} />
            )}
            spelAware={false}
          />
        )}
        {stage.buildDefinitionSource === BuildDefinitionSource.ARTIFACT && (
          <StageArtifactSelectorDelegate
            artifact={get(stage, 'buildDefinitionArtifact.artifact')}
            excludedArtifactTypePatterns={EXCLUDED_ARTIFACT_TYPES}
            expectedArtifactId={get(stage, 'buildDefinitionArtifact.artifactId')}
            label="Build Definition Artifact"
            onArtifactEdited={setArtifact}
            onExpectedArtifactSelected={(artifact: IExpectedArtifact) => setArtifactId(artifact.id)}
            pipeline={props.pipeline}
            selectedArtifactAccount={get(stage, 'buildDefinitionArtifact.artifactAccount')}
            selectedArtifactId={get(stage, 'buildDefinitionArtifact.artifactId')}
            setArtifactAccount={setArtifactAccount}
            setArtifactId={setArtifactId}
            stage={stage}
            updatePipeline={props.updatePipeline}
          />
        )}
        {stage.buildDefinitionSource === BuildDefinitionSource.TRIGGER && (
          <>
            <FormikFormField
              fastField={false}
              name="triggerId"
              label="Trigger Name"
              input={(inputProps: IFormInputProps) => (
                <ReactSelectInput
                  {...inputProps}
                  clearable={false}
                  disabled={!stage.account}
                  isLoading={fetchTriggersStatus === 'PENDING'}
                  options={(fetchTriggersResult || []).map(trigger => ({
                    label: trigger.name,
                    value: trigger.id,
                  }))}
                />
              )}
            />
            <FormikFormField
              fastField={false}
              name="triggerType"
              label="Trigger Type"
              input={(inputProps: IFormInputProps) => (
                <ReactSelectInput
                  {...inputProps}
                  clearable={false}
                  disabled={!stage.triggerId}
                  options={TRIGGER_TYPE_OPTIONS}
                />
              )}
              spelAware={false}
            />
            <FormikFormField
              fastField={false}
              label="Value"
              name={`repoSource.${stage.triggerType}`}
              input={(inputProps: IFormInputProps) => <TextInput {...inputProps} disabled={!stage.triggerType} />}
              spelAware={false}
            />
          </>
        )}
      </div>
    </FormikSpelContextProvider>
  );
}
