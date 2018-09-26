import * as React from 'react';
import { Option } from 'react-select';
import { IPromise } from 'angular';
import { $q } from 'ngimport';
import { Observable, Subject, Subscription } from 'rxjs';

import { IDockerTrigger, ITriggerTemplateComponentProps, Spinner, TetheredSelect } from '@spinnaker/core';

import { DockerImageReader } from '../../image/DockerImageReader';

export interface IDockerTriggerTemplateState {
  tags: string[];
  tagsLoading: boolean;
  loadError: boolean;
  selectedTag: string;
}

export class DockerTriggerTemplate extends React.Component<
  ITriggerTemplateComponentProps,
  IDockerTriggerTemplateState
> {
  private queryStream = new Subject();
  private subscription: Subscription;

  public static formatLabel(trigger: IDockerTrigger): IPromise<string> {
    return $q.when(`(Docker Registry) ${trigger.account ? trigger.account + ':' : ''} ${trigger.repository || ''}`);
  }

  public constructor(props: ITriggerTemplateComponentProps) {
    super(props);
    this.state = {
      tags: [],
      tagsLoading: true,
      loadError: false,
      selectedTag: '',
    };
  }

  private handleQuery = () => {
    const trigger = this.props.command.trigger as IDockerTrigger;
    return Observable.fromPromise(
      DockerImageReader.findTags({
        provider: 'dockerRegistry',
        account: trigger.account,
        repository: trigger.repository,
      }),
    );
  };

  private updateSelectedTag = (tag: string) => {
    const { command } = this.props;
    const trigger = command.trigger as IDockerTrigger;
    command.extraFields.tag = tag;

    if (trigger && trigger.repository) {
      let imageName = '';
      if (trigger.registry) {
        imageName += trigger.registry + '/';
      }
      imageName += trigger.repository;
      command.extraFields.artifacts = [
        {
          type: 'docker/image',
          name: imageName,
          version: tag,
          reference: imageName + ':' + tag,
        },
      ];
    }
    this.setState({ selectedTag: tag });
  };

  private tagLoadSuccess = (tags: string[]) => {
    const { command } = this.props;
    const trigger = command.trigger as IDockerTrigger;
    const newState = {} as IDockerTriggerTemplateState;
    newState.tags = tags;
    if (newState.tags.length) {
      // default to what is supplied by the trigger if possible; otherwise, use the latest
      const defaultSelection = newState.tags.find(t => t === trigger.tag) || newState.tags[0];
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

    this.subscription = this.queryStream
      .debounceTime(250)
      .switchMap(this.handleQuery)
      .subscribe(this.tagLoadSuccess, this.tagLoadFailure);

    // These fields will be added to the trigger when the form is submitted
    command.extraFields = {};

    // cancel search stream if trigger has changed to some other type
    if (command.trigger.type !== 'docker') {
      this.subscription.unsubscribe();
      return;
    }

    this.searchTags();
  };

  public componentWillReceiveProps(nextProps: ITriggerTemplateComponentProps) {
    if (nextProps.command !== this.props.command) {
      this.initialize();
    }
  }

  public componentDidMount() {
    this.initialize();
  }

  private searchTags = (query = '') => {
    this.setState({ tags: [`<span>Finding tags${query && ` matching ${query}`}...</span>`] });
    this.queryStream.next();
  };

  public render() {
    const { tags, tagsLoading, loadError, selectedTag } = this.state;

    const options = tags.map(tag => {
      return { value: tag } as Option<string>;
    });

    return (
      <div className="form-group">
        <label className="col-md-4 sm-label-right">Tag</label>
        {tagsLoading && (
          <div className="col-md-6">
            <div className="form-control-static text-center">
              <Spinner size={'small'} />
            </div>
          </div>
        )}
        {/* prevent form submission while tags are loading */}
        <input type="hidden" required={tagsLoading} value={selectedTag} />
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
                optionRenderer={o => <span>{o.value}</span>}
                clearable={false}
                value={selectedTag}
                valueRenderer={o => (
                  <span>
                    <strong>{o.value}</strong>
                  </span>
                )}
                onChange={(o: Option<string>) => this.updateSelectedTag(o.value)}
              />
            )}
          </div>
        )}
      </div>
    );
  }
}
