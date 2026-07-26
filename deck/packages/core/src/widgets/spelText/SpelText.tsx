import type { StateService } from '@uirouter/core';
import classNames from 'classnames';
import $ from 'jquery';
import 'jquery-textcomplete';
import React from 'react';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { SpelAutocompleteService } from './SpelAutocompleteService';
import type { IPipeline } from '../../domain';
import { ExecutionService } from '../../pipeline/service/execution.service';
import { createCancellableTimeout } from '../../utils/cancellableTimeout';
import { nativePromiseService } from '../../utils/nativePromiseService';

import './spel.less';

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
  private readonly timeoutService = createCancellableTimeout();
  private destroy$ = new Subject();
  private $input: any;

  constructor(props: ISpelTextProps) {
    super(props);
    this.state = { textcompleteConfig: [] };
    this.autocompleteService = new SpelAutocompleteService(
      nativePromiseService,
      new ExecutionService(nativePromiseService, {} as StateService, this.timeoutService as any),
    );
    observableFrom(this.autocompleteService.addPipelineInfo(this.props.pipeline))
      .pipe(takeUntil(this.destroy$))
      .subscribe((textcompleteConfig) => {
        this.setState({ textcompleteConfig: textcompleteConfig });
      });
    this.spelInputRef = React.createRef();
  }

  public componentWillUnmount(): void {
    this.$input.off('change', this.onChange);
    this.destroy$.next();
    this.timeoutService.dispose();
  }

  public componentDidMount(): void {
    this.$input = $(this.spelInputRef.current);
    this.renderSuggestions();
    this.$input.on('change', this.onChange);
  }

  private onChange = (e: any) => {
    this.props.onChange(e.target.value);
  };

  public componentDidUpdate(_: Readonly<ISpelTextProps>, prevState: Readonly<ISpelTextState>): void {
    if (prevState.textcompleteConfig !== this.state.textcompleteConfig) {
      this.renderSuggestions();
    }
  }

  private renderSuggestions() {
    this.$input.attr('contenteditable', 'true');
    this.$input.textcomplete(this.state.textcompleteConfig, {
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
        onChange={this.onChange}
        ref={this.spelInputRef}
      />
    );
  }
}
