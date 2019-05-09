import * as React from 'react';

import 'jquery-textcomplete';
import './spel.less';

import * as classNames from 'classnames';
import * as $ from 'jquery';
import { $q, $http, $timeout } from 'ngimport';
import { SpelAutocompleteService } from './SpelAutocompleteService';
import { ExecutionService } from '../../pipeline/service/execution.service';
import { StateService } from '@uirouter/core';
import { IPipeline } from '../../domain';
import { Observable, Subject } from 'rxjs';

export interface ISpelTextProps {
  placeholder: string;
  value?: string;
  onChange: (value: string) => void;
  pipeline: IPipeline;
  docLink: boolean;
}

export interface ISpelTextState {
  textcompleteConfig: any[];
}

export class SpelText extends React.Component<ISpelTextProps, ISpelTextState> {
  public static defaultProps: Partial<ISpelTextProps> = {
    placeholder: '',
  };

  private autocompleteService: SpelAutocompleteService;
  private readonly spelInputRef: any;
  private destroy$ = new Subject();

  constructor(props: ISpelTextProps) {
    super(props);
    this.state = { textcompleteConfig: [] };
    this.autocompleteService = new SpelAutocompleteService(
      $q,
      new ExecutionService($http, $q, {} as StateService, $timeout),
    );
    Observable.fromPromise(this.autocompleteService.addPipelineInfo(this.props.pipeline))
      .takeUntil(this.destroy$)
      .subscribe(textcompleteConfig => {
        this.setState({ textcompleteConfig: textcompleteConfig });
      });
    this.spelInputRef = React.createRef();
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  public componentDidMount(): void {
    this.renderSuggestions();
  }

  public componentDidUpdate(_: Readonly<ISpelTextProps>, prevState: Readonly<ISpelTextState>): void {
    if (prevState.textcompleteConfig !== this.state.textcompleteConfig) {
      this.renderSuggestions();
    }
  }

  private renderSuggestions() {
    const input = $(this.spelInputRef.current);
    input.attr('contenteditable', 'true');
    input.textcomplete(this.state.textcompleteConfig, {
      maxCount: 1000,
      zIndex: 9000,
      dropdownClassName: 'dropdown-menu textcomplete-dropdown spel-dropdown',
    });
  }

  public render() {
    return (
      <input
        type="text"
        placeholder={this.props.placeholder}
        className={classNames({
          'form-control': true,
          'no-doc-link': !this.props.docLink,
        })}
        value={this.props.value || ''}
        onChange={e => this.props.onChange(e.target.value)}
        ref={this.spelInputRef}
      />
    );
  }
}
