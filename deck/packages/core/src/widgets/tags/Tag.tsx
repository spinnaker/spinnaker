import React from 'react';

import { Key } from '../Keys';

export enum DeleteType {
  BACKSPACE,
  REMOVE,
}

export interface ITag {
  key: string;
  text: string;
}

export interface ITagProps {
  onBlur?: () => void;
  onCreate?: (element: HTMLElement) => void;
  onDelete?: (tag: ITag, deleteType: DeleteType) => void;
  onFocus?: () => void;
  onKeyUp?: (tag: ITag, key: Key) => void;
  tag: ITag;
}

export class Tag extends React.Component<ITagProps> {
  public static defaultProps: Partial<ITagProps> = {
    onBlur: () => {},
    onCreate: () => {},
    onDelete: () => {},
    onFocus: () => {},
    onKeyUp: () => {},
  };

  private refCallback = (element: HTMLElement): void => {
    if (element) {
      this.props.onCreate(element);
    }
  };

  private handleFocus = (): void => {
    this.props.onFocus();
  };

  private handleBlur = (): void => {
    this.props.onBlur();
  };

  private handleKeyUp = (event: React.KeyboardEvent<HTMLElement>): void => {
    switch (event.key) {
      case Key.BACKSPACE:
      case Key.DELETE:
        this.props.onDelete(this.props.tag, DeleteType.BACKSPACE);
        break;
      case Key.LEFT_ARROW:
      case Key.RIGHT_ARROW:
        this.props.onKeyUp(this.props.tag, event.key);
        break;
    }
  };

  private handleRemoveClick = (): void => {
    this.props.onDelete(this.props.tag, DeleteType.REMOVE);
  };

  public render(): React.ReactElement<Tag> {
    const { key, text } = this.props.tag;
    return (
      <div
        ref={this.refCallback}
        className="tag"
        onBlur={this.handleBlur}
        onFocus={this.handleFocus}
        onKeyUp={this.handleKeyUp}
        tabIndex={-1}
      >
        <div className="tag__category">{key.toLocaleUpperCase()}</div>
        <div className="tag__label">{text}</div>
        <div className="tag__remove">
          <i className="fa fa-times-circle" onClick={this.handleRemoveClick} />
        </div>
      </div>
    );
  }
}
