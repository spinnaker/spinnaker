import * as React from 'react';
import { mount } from 'enzyme';
import { createMockStore } from 'redux-test-utils';
import { Provider } from 'react-redux';
import Select from 'react-select';

import { noop, ValidationMessage } from '@spinnaker/core';

import { FilterTemplateSelector, IFilterTemplateSelectorProps } from './filterTemplateSelector';
import { DisableableInput, DisableableTextarea } from 'kayenta/layout/disableable';

describe('<FilterTemplateSelector />', () => {
  let defaultProps: IFilterTemplateSelectorProps;
  beforeEach(() => {
    defaultProps = {
      editedTemplateName: null,
      editedTemplateValue: null,
      selectedTemplateName: 'my-filter-template',
      templates: {
        'my-filter-template': 'metadata.user_labels."app"="${scope}"',
        'my-other-filter-template': 'metadata.user_labels."app"="${location}"',
      },
      validation: { warnings: {}, errors: {} },
      deleteTemplate: noop,
      editTemplateBegin: noop,
      editTemplateCancel: noop,
      editTemplateConfirm: noop,
      editTemplateName: noop,
      editTemplateValue: noop,
      selectTemplate: noop,
    };
  });
  it('builds options from filter template map', () => {
    const component = buildComponent(defaultProps);

    expect(
      component
        .find(Select)
        .first()
        .props()
        .options.map(o => o.value),
    ).toEqual(['my-filter-template', 'my-other-filter-template', null]); // null is "Create new" option
  });

  it('renders filter template', () => {
    let component = buildComponent(defaultProps);

    let pre = component.find('pre').first();
    expect(pre.html()).toContain('${scope}');

    component = buildComponent({
      ...defaultProps,
      selectedTemplateName: 'my-other-filter-template',
    });
    pre = component.find('pre').first();
    expect(pre.html()).toContain('${location}');
  });

  it('does not render filter template if not selected', () => {
    const component = buildComponent({
      ...defaultProps,
      selectedTemplateName: null,
    });
    expect(component.find('pre').length).toEqual(0);
  });

  it('renders errors when appropriate', () => {
    let component = buildComponent(defaultProps);
    expect(component.find(ValidationMessage).length).toEqual(0);

    component = buildComponent({
      ...defaultProps,
      editedTemplateName: '',
      editedTemplateValue: 'metadata.user_labels."app"="${scope}"',
      validation: {
        warnings: {},
        errors: {
          templateName: {
            message: 'Template name is required',
          },
        },
      },
    });
    expect(
      component
        .find(ValidationMessage)
        .first()
        .props().message,
    ).toEqual('Template name is required');
  });

  it('renders input and textarea when editing template', () => {
    let component = buildComponent(defaultProps);
    expect(component.find(DisableableInput).length).toEqual(0);
    expect(component.find(DisableableTextarea).length).toEqual(0);

    component = buildComponent({
      ...defaultProps,
      editedTemplateName: 'edited name',
      editedTemplateValue: 'edited value',
    });
    expect(component.find(DisableableInput).length).toEqual(1);
    expect(component.find(DisableableTextarea).length).toEqual(1);
  });
});

const buildComponent = (props: IFilterTemplateSelectorProps) =>
  mount(
    <Provider store={createMockStore()}>
      <FilterTemplateSelector {...props} />
    </Provider>,
  ).find(FilterTemplateSelector);
