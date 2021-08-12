import { FormikProps } from 'formik';
import React from 'react';

import { Application } from '../../../../application';
import { SETTINGS } from '../../../../config/settings';
import { IGitTrigger } from '../../../../domain';
import { HelpField } from '../../../../help';
import { FormikFormField, ReactSelectInput, TextInput } from '../../../../presentation';

export interface IGitTriggerConfigProps {
  formik: FormikProps<IGitTrigger>;
  trigger: IGitTrigger;
  application: Application;
  triggerUpdated: (trigger: IGitTrigger) => void;
}

const gitTriggerTypes = SETTINGS.gitSources || ['stash', 'github', 'bitbucket', 'gitlab'];

const displayTexts = {
  bitbucket: {
    projectLabel: 'Team or User',
    projectPlaceholder: 'Team or User name, i.e. spinnaker for bitbucket.org/spinnaker/echo',
    slugLabel: 'Repo name',
    slugPlaceholder: 'Repository name (not the url), i.e, echo for bitbucket.org/spinnaker/echo',
  },
  github: {
    projectLabel: 'Organization or User',
    projectPlaceholder: 'Organization or User name, i.e. spinnaker for github.com/spinnaker/echo',
    slugLabel: 'Project',
    slugPlaceholder: 'Project name (not the url), i.e, echo for github.com/spinnaker/echo',
  },
  gitlab: {
    projectLabel: 'Organization or User',
    projectPlaceholder: 'Organization or User name, i.e. spinnaker for gitlab.com/spinnaker/echo',
    slugLabel: 'Project',
    slugPlaceholder: 'Project name (not the url), i.e. echo for gitlab.com/spinnaker/echo',
  },
  stash: {
    projectLabel: 'Project',
    projectPlaceholder: 'Project name, i.e. SPKR for stash.mycorp.com/projects/SPKR/repos/echo',
    slugLabel: 'Repo name',
    slugPlaceholder: 'Repository name (not the url), i.e, echo for stash.mycorp.com/projects/SPKR/repos/echo',
  },
};

export function GitTrigger(gitTriggerProps: IGitTriggerConfigProps) {
  const { formik, application } = gitTriggerProps;
  const trigger = formik.values;
  const { source } = formik.values;
  const { projectLabel, projectPlaceholder, slugLabel, slugPlaceholder } = displayTexts[source || 'github'];

  React.useEffect(() => {
    // If no source is set, apply initial values
    if (!trigger.source) {
      const attributes = application.attributes || {};
      const defaultSource = attributes.repoType || (gitTriggerTypes.length === 1 ? gitTriggerTypes[0] : null);
      formik.setFieldValue('source', defaultSource);
      formik.setFieldValue('project', attributes.repoProjectKey);
      formik.setFieldValue('slug', attributes.repoSlug);
    }
  }, []);

  React.useEffect(() => {
    if (trigger.source !== 'github') {
      formik.setFieldValue('secret', undefined);
    }
  }, [trigger.source]);

  return (
    <>
      {gitTriggerTypes && gitTriggerTypes.length > 1 && (
        <FormikFormField
          name="source"
          label="Repo Type"
          input={(props) => (
            <ReactSelectInput
              {...props}
              placeholder="Select Repo Type"
              stringOptions={gitTriggerTypes}
              clearable={false}
            />
          )}
        />
      )}
      {SETTINGS.stashTriggerInfo && trigger.source === 'stash' && (
        <div className="flex-container-h center">
          <a className="sp-margin-m-yaxis" href={SETTINGS.stashTriggerInfo} target="_blank">
            See how to add a stash trigger to Spinnaker
          </a>
        </div>
      )}
      <FormikFormField
        name="project"
        label={projectLabel}
        required={true}
        input={(props) => <TextInput {...props} placeholder={projectPlaceholder} />}
      />

      <FormikFormField
        name="slug"
        label={slugLabel}
        required={true}
        validate={(value: string, label) =>
          value && value.match(/:\/\//) && `${label} is a simple name should not contain ://`
        }
        input={(props) => <TextInput {...props} placeholder={slugPlaceholder} />}
      />

      <FormikFormField
        name="branch"
        label="Branch"
        help={<HelpField id="pipeline.config.git.trigger.branch" />}
        input={(props) => <TextInput {...props} />}
      />

      {source === 'github' && (
        <FormikFormField
          name="secret"
          label="Secret"
          help={<HelpField id="pipeline.config.git.trigger.githubSecret" />}
          input={(props) => <TextInput {...props} />}
        />
      )}
    </>
  );
}
