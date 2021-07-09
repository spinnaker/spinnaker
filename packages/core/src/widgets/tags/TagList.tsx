import React from 'react';

import { Key } from '../Keys';
import { DeleteType, ITag, Tag } from './Tag';
import { logger } from '../../utils';

import './taglist.less';

export interface ITagListProps {
  tags: ITag[];
  onBlur?: () => void;
  onDelete?: (tag: ITag, focus: boolean) => void;
  onFocus?: () => void;
  onKeyUp?: () => void;
  onUpdate?: (elements: HTMLElement[]) => void;
}

export class TagList extends React.Component<ITagListProps> {
  public static defaultProps: Partial<ITagListProps> = {
    onBlur: () => {},
    onDelete: () => {},
    onFocus: () => {},
    onKeyUp: () => {},
    onUpdate: () => {},
  };

  private tagElements: HTMLElement[] = [];

  private findIndex(tag: ITag): number {
    return this.props.tags.findIndex((t: ITag) => {
      return tag === t;
    });
  }

  private handleBlur = (): void => {
    this.props.onBlur();
  };

  private handleCreate = (element: HTMLElement): void => {
    this.tagElements.push(element);
    this.props.onUpdate(this.tagElements);
  };

  private handleDelete = (tag: ITag, deleteType: DeleteType): void => {
    const index = this.findIndex(tag);
    this.tagElements.splice(index, 1);
    this.props.onUpdate(this.tagElements);

    // if the array of tag DOM elements is empty (e.g., we deleted the last tag), or if the user
    // deleted the tag via the `x`, then propagate the event.
    // otherwise:
    // if we deleted the first tag, select the next tag
    // if we deleted second through the n-1st tag, select the next tag immediately after the deleted one
    // if we deleted the nth tag, the select the n-1st tag
    // and then finally, propagate the event
    switch (deleteType) {
      case DeleteType.BACKSPACE:
        if (this.tagElements.length) {
          if (index === 0) {
            this.tagElements[0].focus();
          } else {
            if (index <= this.tagElements.length - 1) {
              this.tagElements[index].focus();
            } else {
              this.tagElements[index - 1].focus();
            }
          }
          this.props.onDelete(tag, false);
        } else {
          this.props.onDelete(tag, true);
        }
        break;
      case DeleteType.REMOVE:
        this.props.onDelete(tag, true);
        break;
    }

    logger.log({ category: 'Infrastructure Search Tags', action: 'Individual tag removed' });
  };

  private handleFocus = (): void => {
    this.props.onFocus();
  };

  private handleKeyUp = (tag: ITag, key: Key): void => {
    const index = this.findIndex(tag);
    switch (key) {
      case Key.LEFT_ARROW:
        if (index === 0) {
          this.props.onKeyUp();
        } else {
          this.tagElements[index - 1].focus();
        }
        break;
      case Key.RIGHT_ARROW:
        if (index === this.tagElements.length - 1) {
          this.props.onKeyUp();
        } else {
          this.tagElements[index + 1].focus();
        }
        break;
    }
  };

  public render(): React.ReactElement<TagList> {
    const tags = this.props.tags || [];

    return (
      <div className="tag-list">
        {tags.map((tag) => (
          <Tag
            key={[tag.key, tag.text].join('|')}
            tag={tag}
            onBlur={this.handleBlur}
            onCreate={this.handleCreate}
            onDelete={this.handleDelete}
            onFocus={this.handleFocus}
            onKeyUp={this.handleKeyUp}
          />
        ))}
      </div>
    );
  }
}
