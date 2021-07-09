import { get } from 'lodash';
import { $q } from 'ngimport';
import React from 'react';
import { Option } from 'react-select';
import { from as observableFrom, Subject, Subscription } from 'rxjs';
import { debounceTime, switchMap } from 'rxjs/operators';

import {
  HelpField,
  IDockerTrigger,
  IPipelineCommand,
  ITriggerTemplateComponentProps,
  Spinner,
  TetheredSelect,
} from '@spinnaker/core';

import { DockerImageReader, IDockerLookupType } from '../../image';

const lookupTypeOptions = [
  { value: 'digest', label: 'Digest' },
  { value: 'tag', label: 'Tag' },
];

export interface IDockerTriggerTemplateState {
  digest: string;
  tags: string[];
  tagsLoading: boolean;
  loadError: boolean;
  lookupType: string;
  selectedTag: string;
}

export class DockerTriggerTemplate extends React.Component<
  ITriggerTemplateComponentProps,
  IDockerTriggerTemplateState
> {
  private queryStream = new Subject();
  private subscription: Subscription;

  public static formatLabel(trigger: IDockerTrigger): PromiseLike<string> {
    return $q.when(`(Docker Registry) ${trigger.account ? trigger.account + ':' : ''} ${trigger.repository || ''}`);
  }

  public constructor(props: ITriggerTemplateComponentProps) {
    super(props);
    this.state = {
      digest: '',
      tags: [],
      tagsLoading: true,
      loadError: false,
      lookupType: 'tag',
      selectedTag: '',
    };
  }

  private handleQuery = () => {
    const trigger = this.props.command.trigger as IDockerTrigger;
    return observableFrom(
      DockerImageReader.findTags({
        provider: 'dockerRegistry',
        account: trigger.account,
        repository: trigger.repository,
      }),
    );
  };

  private lookupTypeChanged = (o: Option<IDockerLookupType>) => {
    const newType = o.value;
    this.updateArtifact(this.props.command, newType === 'tag' ? this.state.selectedTag : this.state.digest);
    this.setState({ lookupType: newType });
  };

  private updateArtifact(command: IPipelineCommand, tagOrDigest: string) {
    this.props.updateCommand('extraFields.tag', tagOrDigest);
    const trigger = command.trigger as IDockerTrigger;
    if (trigger && trigger.repository) {
      let imageName = '';
      if (trigger.registry) {
        imageName += trigger.registry + '/';
      }
      imageName += trigger.repository;

      let imageReference = '';
      if (this.state.lookupType === 'digest') {
        imageReference = `${imageName}@${tagOrDigest}`;
      } else {
        imageReference = `${imageName}:${tagOrDigest}`;
      }

      this.props.updateCommand('extraFields.artifacts', [
        {
          type: 'docker/image',
          name: imageName,
          version: tagOrDigest,
          reference: imageReference,
        },
      ]);
    }
  }

  private updateSelectedTag = (tag: string) => {
    this.updateArtifact(this.props.command, tag);
    this.setState({ selectedTag: tag });
    this.props.command.triggerInvalid = false;
  };

  private updateDigest = (digest: string) => {
    this.updateArtifact(this.props.command, digest);
    this.setState({ digest });
  };

  private tagLoadSuccess = (tags: string[]) => {
    const { command } = this.props;
    const trigger = command.trigger as IDockerTrigger;
    const newState = {} as IDockerTriggerTemplateState;
    newState.tags = tags || [];
    // default to what is supplied by the trigger if possible
    const defaultSelection = newState.tags.find((t) => t === trigger.tag);
    if (defaultSelection) {
      newState.selectedTag = defaultSelection;
      this.updateSelectedTag(defaultSelection);
    }
    newState.tagsLoading = false;
    this.setState(newState);
  };

  private tagLoadFailure = () => {
    this.setState({
      tagsLoading: false,
      loadError: true,
    });
  };

  private initialize = () => {
    const { command } = this.props;
    this.props.updateCommand('triggerInvalid', true);

    // These fields will be added to the trigger when the form is submitted
    this.props.updateCommand('extraFields', {
      tag: get(command, 'extraFields.tag', ''),
      artifacts: get(command, 'extraFields.artifacts', ''),
    });

    if (this.subscription) {
      this.subscription.unsubscribe();
    }

    // cancel search stream if trigger has changed to some other type
    if (command.trigger.type !== 'docker') {
      return;
    }

    this.subscription = this.queryStream
      .pipe(debounceTime(250), switchMap(this.handleQuery))
      .subscribe(this.tagLoadSuccess, this.tagLoadFailure);

    this.searchTags();
  };

  public componentDidMount() {
    this.initialize();
  }

  public componentWillUnmount() {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  private searchTags = (query = '') => {
    this.setState({ tags: [`<span>Finding tags${query && ` matching ${query}`}...</span>`] });
    this.queryStream.next();
  };

  public render() {
    const { digest, tags, tagsLoading, loadError, selectedTag, lookupType } = this.state;

    const options = tags.map((tag) => {
      return { value: tag } as Option<string>;
    });

    return (
      <>
        <div className="form-group">
          <div className="sm-label-right col-md-4">Type</div>
          <div className="col-md-3">
            <TetheredSelect
              clearable={false}
              value={lookupType}
              options={lookupTypeOptions}
              onChange={this.lookupTypeChanged}
            />
          </div>
        </div>
        {lookupType === 'tag' && (
          <div className="form-group">
            <label className="col-md-4 sm-label-right">Tag</label>
            {tagsLoading && (
              <div className="col-md-6">
                <div className="form-control-static text-center">
                  <Spinner size="small" />
                </div>
              </div>
            )}
            {loadError && <div className="col-md-6">Error loading tags!</div>}
            {!tagsLoading && (
              <div className="col-md-6">
                {tags.length === 0 && (
                  <div>
                    <p className="form-control-static">No tags found</p>
                  </div>
                )}
                {tags.length > 0 && (
                  <TetheredSelect
                    options={options}
                    optionRenderer={(o) => <span>{o.value}</span>}
                    clearable={false}
                    value={selectedTag}
                    valueRenderer={(o) => (
                      <span>
                        <strong>{o.value}</strong>
                      </span>
                    )}
                    onChange={(o: Option<string>) => this.updateSelectedTag(o.value)}
                    placeholder="Search tags..."
                  />
                )}
              </div>
            )}
          </div>
        )}
        {lookupType === 'digest' && (
          <div className="form-group">
            <label className="col-md-4 sm-label-right">
              Digest <HelpField id="pipeline.config.docker.trigger.digest" />
            </label>
            <div className="col-md-6">
              <input
                value={digest}
                onChange={(e) => this.updateDigest(e.target.value)}
                className="form-control input-sm"
                required={true}
              />
            </div>
          </div>
        )}
      </>
    );
  }
}
