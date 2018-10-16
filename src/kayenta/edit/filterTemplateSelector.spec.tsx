import * as React from 'react';
import { mount } from 'enzyme';
import { createMockStore } from 'redux-test-utils';
import { Provider } from 'react-redux';
import Select from 'react-select';

import { noop } from '@spinnaker/core';
import { FilterTemplateSelector, IFilterTemplateSelectorProps } from './filterTemplateSelector';

describe('<FilterTemplateSelector />', () => {
  it('builds options from filter template map', () => {
    const component = buildComponent({
      template: 'my-filter-template',
      templates: {
        'my-filter-template': 'metadata.user_labels."app"="${scope}"',
        'my-other-filter-template': 'metadata.user_labels."app"="${location}"',
      },
    });

    expect(
      component
        .find(Select)
        .first()
        .props()
        .options.map(o => o.value),
    ).toEqual(['my-filter-template', 'my-other-filter-template']);
  });

  it('renders filter template', () => {
    let component = buildComponent({
      template: 'my-filter-template',
      templates: {
        'my-filter-template': 'metadata.user_labels."app"="${scope}"',
        'my-other-filter-template': 'metadata.user_labels."app"="${location}"',
      },
    });

    let pre = component.find('pre').first();
    expect(pre.html()).toContain('${scope}');

    component = buildComponent({
      template: 'my-other-filter-template',
      templates: {
        'my-filter-template': 'metadata.user_labels."app"="${scope}"',
        'my-other-filter-template': 'metadata.user_labels."app"="${location}"',
      },
    });
    pre = component.find('pre').first();
    expect(pre.html()).toContain('${location}');
  });

  it('does not render filter template if not present', () => {
    ['my-third-filter-template', '', null].forEach(template => {
      const component = buildComponent({
        template,
        templates: {
          'my-filter-template': 'metadata.user_labels."app"="${scope}"',
          'my-other-filter-template': 'metadata.user_labels."app"="${location}"',
        },
      });

      expect(component.find('pre').length).toEqual(0);
    });
  });
});

const buildComponent = (props: Partial<IFilterTemplateSelectorProps>) =>
  mount(
    <Provider store={createMockStore()}>
      <FilterTemplateSelector template={props.template} templates={props.templates} select={noop} />
    </Provider>,
  ).find(FilterTemplateSelector);
