import * as React from 'react';
import { has } from 'lodash';

import { Application } from 'core/application';
import { BaseTrigger } from 'core/pipeline';
import { HelpField } from 'core/help';
import { IGitTrigger } from 'core/domain';
import { SETTINGS } from 'core/config/settings';
import { FormField, ReactSelectInput, TextInput } from 'core/presentation';

export interface IGitTriggerConfigProps {
  trigger: IGitTrigger;
  application: Application;
  triggerUpdated: (trigger: IGitTrigger) => void;
}

export class GitTrigger extends React.Component<IGitTriggerConfigProps> {
  private gitTriggerTypes = SETTINGS.gitSources || ['stash', 'github', 'bitbucket', 'gitlab'];
  private displayText: any = {
    bitbucket: {
      'pipeline.config.git.project': 'Team or User',
      'pipeline.config.git.slug': 'Repo name',
      project: 'Team or User name, i.e. spinnaker for bitbucket.org/spinnaker/echo',
      slug: 'Repository name (not the url), i.e, echo for bitbucket.org/spinnaker/echo',
    },
    github: {
      'pipeline.config.git.project': 'Organization or User',
      'pipeline.config.git.slug': 'Project',
      project: 'Organization or User name, i.e. spinnaker for github.com/spinnaker/echo',
      slug: 'Project name (not the url), i.e, echo for github.com/spinnaker/echo',
    },
    gitlab: {
      'pipeline.config.git.project': 'Organization or User',
      'pipeline.config.git.slug': 'Project',
      project: 'Organization or User name, i.e. spinnaker for gitlab.com/spinnaker/echo',
      slug: 'Project name (not the url), i.e. echo for gitlab.com/spinnaker/echo',
    },
    stash: {
      'pipeline.config.git.project': 'Project',
      'pipeline.config.git.slug': 'Repo name',
      project: 'Project name, i.e. SPKR for stash.mycorp.com/projects/SPKR/repos/echo',
      slug: 'Repository name (not the url), i.e, echo for stash.mycorp.com/projects/SPKR/repos/echo',
    },
  };

  constructor(props: IGitTriggerConfigProps) {
    super(props);
    this.state = {};
  }

  public componentDidMount() {
    const trigger = { ...this.props.trigger };
    const { attributes } = this.props.application;

    if (has(attributes, 'repoProjectKey') && !this.props.trigger.source) {
      trigger.source = attributes.repoType;
      trigger.project = attributes.repoProjectKey;
      trigger.slug = attributes.repoSlug;
    }
    if (this.gitTriggerTypes.length === 1) {
      trigger.source = this.gitTriggerTypes[0];
    }

    this.props.triggerUpdated && this.props.triggerUpdated(trigger);
  }

  private onUpdateTrigger = (update: any) => {
    this.props.triggerUpdated &&
      this.props.triggerUpdated({
        ...this.props.trigger,
        ...update,
      });
  };

  private GitTriggerContents = () => {
    const { trigger } = this.props;
    const { branch, project, secret, slug, source } = trigger;
    const displayText = this.displayText[source ? source : 'github'];

    return (
      <>
        {this.gitTriggerTypes && this.gitTriggerTypes.length > 1 && (
          <FormField
            label="Repo Type"
            value={source}
            onChange={e => this.onUpdateTrigger({ source: e.target.value })}
            input={props => (
              <ReactSelectInput
                {...props}
                placeholder="Select Repo Type"
                stringOptions={this.gitTriggerTypes}
                clearable={false}
              />
            )}
          />
        )}

        <FormField
          label={displayText['pipeline.config.git.project']}
          value={project}
          onChange={e => this.onUpdateTrigger({ project: e.target.value })}
          required={true}
          input={props => <TextInput {...props} placeholder={displayText['project']} />}
        />

        <FormField
          label={displayText['pipeline.config.git.slug']}
          value={slug}
          onChange={e => this.onUpdateTrigger({ slug: e.target.value })}
          required={true}
          validate={(value: string, label) =>
            value && value.match(/:\/\//) && `${label} is a simple name should not contain ://`
          }
          input={props => <TextInput {...props} placeholder={displayText['slug']} />}
        />

        <FormField
          label="Branch"
          help={<HelpField id="pipeline.config.git.trigger.branch" />}
          value={branch}
          onChange={e => this.onUpdateTrigger({ branch: e.target.value })}
          input={props => <TextInput {...props} />}
        />

        {'github' === source && (
          <FormField
            label="Secret"
            help={<HelpField id="pipeline.config.git.trigger.githubSecret" />}
            value={secret}
            onChange={e => this.onUpdateTrigger({ secret: e.target.value })}
            input={props => <TextInput {...props} />}
          />
        )}
      </>
    );
  };

  public render() {
    const { GitTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<GitTriggerContents />} />;
  }
}
