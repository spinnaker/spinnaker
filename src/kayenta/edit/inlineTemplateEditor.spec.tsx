import * as React from 'react';
import { createMockStore } from 'redux-test-utils';
import { Provider } from 'react-redux';
import { mount } from 'enzyme';
import { identity } from 'lodash';

import { noop, ValidationMessage } from '@spinnaker/core';

import { IInlineTemplateEditorProps, InlineTemplateEditor } from './inlineTemplateEditor';
import { DisableableTextarea } from '../layout/disableable';

describe('<InlineTemplateEditor />', () => {
  it('renders a textarea with template value', () => {
    const component = buildComponent({
      templateValue: 'metadata.user_labels."app"="${scope}"',
      transformValueForSave: identity,
      editTemplateValue: noop,
    });
    expect(
      component
        .find(DisableableTextarea)
        .first()
        .props().value,
    ).toEqual('metadata.user_labels."app"="${scope}"');
  });
  it('renders an error for empty input', () => {
    let component = buildComponent({
      templateValue: 'metadata.user_labels."app"="${scope}"',
      transformValueForSave: identity,
      editTemplateValue: noop,
    });
    expect(component.find(ValidationMessage).length).toEqual(0);
    component = buildComponent({
      templateValue: '',
      transformValueForSave: identity,
      editTemplateValue: noop,
    });
    expect(component.find(ValidationMessage).props().message).toEqual('Template is required');
  });
});

const buildComponent = (props: IInlineTemplateEditorProps) =>
  mount(
    <Provider store={createMockStore()}>
      <InlineTemplateEditor {...props} />
    </Provider>,
  ).find(InlineTemplateEditor);
