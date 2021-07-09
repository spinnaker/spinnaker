import { set } from 'lodash';
import React from 'react';
import { Option } from 'react-select';

import { IEntityTag } from '../../../../domain';
import { HelpField } from '../../../../help/HelpField';
import { FormField, ReactSelectInput, TextInput } from '../../../../presentation';
import { UUIDGenerator } from '../../../../utils';

import './TagEditor.less';

export type EntityTagType = 'notice' | 'alert' | 'custom';

export interface ITagEditorProps {
  onChange: (tag: IEntityTag) => void;
  tag: IEntityTag;
}

export interface ITagEditorState {
  type: EntityTagType;
}

const typeOptions: Array<Option<EntityTagType>> = [
  { label: 'Notice', value: 'notice' },
  { label: 'Alert', value: 'alert' },
  { label: 'Custom', value: 'custom' },
];

export class TagEditor extends React.Component<ITagEditorProps, ITagEditorState> {
  constructor(props: ITagEditorProps) {
    super(props);

    const type = props.tag.name.startsWith('spinnaker_ui_notice')
      ? 'notice'
      : props.tag.name.startsWith('spinnaker_ui_alert')
      ? 'alert'
      : 'custom';

    this.state = {
      type,
    };
  }

  private handleTypeChanged = (type: EntityTagType) => {
    this.setState({ type });

    const tag = { ...this.props.tag };
    if (type !== 'custom') {
      delete tag.namespace;
      tag.name = `spinnaker_ui_${type}:${UUIDGenerator.generateUuid()}`;
      tag.value = {
        type,
        message: '',
      };
    } else {
      tag.name = '';
      tag.value = '';
    }
    this.props.onChange(tag);
  };

  private tagValueChanged = (key: string, value: any) => {
    if (key === 'value') {
      try {
        value = JSON.parse(value);
      } catch (ignored) {
        /* noop */
      }
    }
    const tag = { ...this.props.tag };
    set(tag, key, value);
    this.props.onChange(tag);
  };

  public render() {
    const { tag } = this.props;
    const { type } = this.state;

    const valueIsObject = typeof tag.value === 'object';
    const value = valueIsObject ? JSON.stringify(this.props.tag.value) : this.props.tag.value;

    const namespaceInput =
      type === 'custom' ? (
        <div className="row">
          <label className="col-md-3 sm-label-right">
            Namespace <HelpField id="pipeline.config.entitytags.namespace" />
          </label>
          <div className="col-md-8">
            <FormField
              input={(props) => <TextInput {...props} />}
              value={tag.namespace || ''}
              onChange={(event: any) => this.tagValueChanged('namespace', event.target.value)}
            />
          </div>
        </div>
      ) : null;

    const nameInput =
      type === 'custom' ? (
        <div className="row">
          <label className="col-md-3 sm-label-right">Name</label>
          <div className="col-md-8">
            <FormField
              input={(props) => <TextInput {...props} />}
              value={tag.name || ''}
              onChange={(event: any) => this.tagValueChanged('name', event.target.value)}
            />
          </div>
        </div>
      ) : null;

    const valueInput =
      type === 'custom' ? (
        <div className="row">
          <label className="col-md-3 sm-label-right">
            Value <HelpField id="pipeline.config.entitytags.value" />
          </label>
          <div className="col-md-8">
            <FormField
              input={(props) => <TextInput {...props} />}
              value={value || ''}
              onChange={(event: any) => this.tagValueChanged('value', event.target.value)}
            />
          </div>
        </div>
      ) : (
        <div className="row">
          <label className="col-md-3 sm-label-right">Message</label>
          <div className="col-md-8">
            <FormField
              input={(props) => <TextInput {...props} />}
              value={tag.value.message || ''}
              onChange={(event: any) => this.tagValueChanged('value.message', event.target.value)}
            />
          </div>
        </div>
      );

    return (
      <div className="TagEditor">
        <div className="row">
          <label className="col-md-3 sm-label-right">Type</label>
          <div className="col-md-8">
            <FormField
              input={(props) => (
                <ReactSelectInput {...props} options={typeOptions} clearable={false} className="full-width" />
              )}
              required={true}
              onChange={(e: any) => this.handleTypeChanged(e.target.value)}
              value={type}
            />
          </div>
        </div>
        {namespaceInput}
        {nameInput}
        {valueInput}
      </div>
    );
  }
}
