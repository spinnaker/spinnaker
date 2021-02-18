import { includes } from 'lodash';
import React from 'react';

interface IServiceTagsInputProps {
  tags: string[];
  onChange: (e: string[]) => void;
}

interface IServiceTagsInputState {
  newTag: string;
  tags: string[];
}

export class ServiceTagsInput extends React.Component<IServiceTagsInputProps, IServiceTagsInputState> {
  constructor(props: IServiceTagsInputProps) {
    super(props);
    this.state = {
      newTag: '',
      tags: this.props.tags || [],
    };
  }

  private newTagUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.setState({ newTag: event.target.value });
  };

  private addTag = (): void => {
    const { onChange, tags } = this.props;
    const { newTag } = this.state;
    const currentTags = tags || [];

    if (!includes(currentTags, newTag.trim())) {
      const newTags = [...currentTags, newTag.trim()].sort((a, b) => a.localeCompare(b));
      this.setState({
        tags: newTags,
        newTag: '',
      });
      onChange(newTags);
    }
  };

  private deleteTag = (index: number): void => {
    const { onChange, tags } = this.props;
    const newTags = tags.slice();
    newTags.splice(index, 1);
    this.setState({ tags: newTags });
    onChange(newTags);
  };

  public render() {
    const { tags, newTag } = this.state;
    return (
      <>
        <div className="row">
          <div className="col-md-4">
            <input type="text" className="form-control" name="newTag" onChange={this.newTagUpdated} value={newTag} />
          </div>
          <div className="col-md-2">
            <button disabled={!newTag.trim()} className="btn btn-default btn-sm btn-add-tag" onClick={this.addTag}>
              Add Tag
            </button>
          </div>
        </div>
        <div>
          <div>
            {tags &&
              tags.map((tag: any, index: number) => {
                return (
                  <span className="badge badge-pill" key={index}>
                    &nbsp;
                    {tag}
                    &nbsp;
                    <button
                      type="button"
                      className="close small"
                      aria-label="Close"
                      onClick={() => this.deleteTag(index)}
                    >
                      <span aria-hidden="true">&times;</span>
                    </button>
                  </span>
                );
              })}
          </div>
        </div>
      </>
    );
  }
}
