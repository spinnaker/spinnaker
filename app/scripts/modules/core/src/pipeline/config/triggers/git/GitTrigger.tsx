import * as React from 'react';
import Select, { Option } from 'react-select';
import { has } from 'lodash';

import { Application } from 'core/application';
import { BaseTrigger } from 'core/pipeline';
import { HelpField } from 'core/help';
import { IGitTrigger } from 'core/domain';
import { SETTINGS } from 'core/config/settings';
import { TextInput } from 'core/presentation';

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
          <div className="form-group">
            <label className="col-md-3 sm-label-right">
              <span>Repo Type </span>
            </label>
            <div className="col-md-6">
              <Select
                className="form-control input-sm"
                onChange={(option: Option<string>) => this.onUpdateTrigger({ source: option.value })}
                options={this.gitTriggerTypes.map(type => ({ label: type, value: type }))}
                placeholder="Select Repo Type"
                value={source}
              />
            </div>
          </div>
        )}
        <div className="form-group">
          <label className="col-md-3 sm-label-right">
            <span>{displayText['pipeline.config.git.project']} </span>
          </label>

          <div className="col-md-9">
            <TextInput
              className="form-control input-sm"
              name="project"
              onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
                this.onUpdateTrigger({ project: event.target.value })
              }
              placeholder={displayText['project']}
              required={true}
              value={project}
            />
          </div>
        </div>
        <div className="form-group">
          <label className="col-md-3 sm-label-right">
            <span>{displayText['pipeline.config.git.slug']} </span>
          </label>
          <div className="col-md-9">
            <TextInput
              className="form-control input-sm"
              name="slug"
              onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
                this.onUpdateTrigger({ slug: event.target.value })
              }
              placeholder={displayText['slug']}
              pattern="^((?!://).)*$"
              required={true}
              value={slug}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <span className="label-text">Branch </span>
            <HelpField id="pipeline.config.git.trigger.branch" />
          </div>
          <div className="col-md-9">
            <TextInput
              className="form-control input-sm"
              onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
                this.onUpdateTrigger({ branch: event.target.value })
              }
              value={branch}
            />
          </div>
        </div>
        {'github' === source && (
          <div className="form-group">
            <div className="col-md-3 sm-label-right">
              <span>Secret </span>
              <HelpField id="pipeline.config.git.trigger.githubSecret" />
            </div>
            <div className="col-md-9">
              <TextInput
                className="form-control input-sm"
                onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
                  this.onUpdateTrigger({ secret: event.target.value })
                }
                value={secret}
              />
            </div>
          </div>
        )}
      </>
    );
  };

  public render() {
    const { GitTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<GitTriggerContents />} />;
  }
}
