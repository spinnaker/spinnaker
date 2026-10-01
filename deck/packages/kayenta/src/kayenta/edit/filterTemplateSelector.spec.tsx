import { fireEvent, render, screen, within } from '@testing-library/react';
import { setupUser } from '../../../../core/src/utils/testUtils/userEvent';
import * as React from 'react';
import { Provider } from 'react-redux';
import { createStore } from 'redux';

import { noop } from '@spinnaker/core';

import { FilterTemplateSelector, IFilterTemplateSelectorProps } from './filterTemplateSelector';

const buildComponent = (props: IFilterTemplateSelectorProps) =>
  render(
    <Provider store={createStore(() => ({}))}>
      <FilterTemplateSelector {...props} />
    </Provider>,
  );

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
  it('builds options from filter template map and selects a template', async () => {
    const user = setupUser();
    const selectTemplate = vi.fn();
    buildComponent({ ...defaultProps, selectTemplate });

    fireEvent.keyDown(screen.getByRole('combobox'), { key: 'ArrowDown', keyCode: 40 });

    const menu = within(screen.getByRole('listbox'));
    expect(menu.getByRole('option', { name: 'my-filter-template' })).toBeVisible();
    expect(menu.getByRole('option', { name: 'my-other-filter-template' })).toBeVisible();
    expect(menu.getByRole('option', { name: 'Create new...' })).toBeVisible();

    await user.click(menu.getByRole('option', { name: 'my-other-filter-template' }));

    expect(selectTemplate).toHaveBeenCalledWith({
      label: 'my-other-filter-template',
      requestingNew: false,
      value: 'my-other-filter-template',
    });
  });

  it('renders filter template', () => {
    const { rerender } = buildComponent(defaultProps);

    expect(screen.getByText(/\$\{scope\}/)).toBeVisible();

    rerender(
      <Provider store={createStore(() => ({}))}>
        <FilterTemplateSelector {...defaultProps} selectedTemplateName="my-other-filter-template" />
      </Provider>,
    );
    expect(screen.getByText(/\$\{location\}/)).toBeVisible();
  });

  it('does not render filter template if not selected', () => {
    buildComponent({
      ...defaultProps,
      selectedTemplateName: null,
    });
    expect(screen.queryByText(/metadata\.user_labels/)).not.toBeInTheDocument();
  });

  it('renders errors when appropriate', () => {
    const { rerender } = buildComponent(defaultProps);
    expect(screen.queryByText('Template name is required')).not.toBeInTheDocument();

    rerender(
      <Provider store={createStore(() => ({}))}>
        <FilterTemplateSelector
          {...defaultProps}
          editedTemplateName=""
          editedTemplateValue={'metadata.user_labels."app"="${scope}"'}
          validation={{
            warnings: {},
            errors: {
              templateName: {
                message: 'Template name is required',
              },
            },
          }}
        />
      </Provider>,
    );
    expect(screen.getByText('Template name is required')).toBeVisible();
  });

  it('renders input and textarea when editing template', () => {
    const { rerender } = buildComponent(defaultProps);
    expect(screen.queryAllByRole('textbox')).toHaveLength(0);

    rerender(
      <Provider store={createStore(() => ({}))}>
        <FilterTemplateSelector {...defaultProps} editedTemplateName="edited name" editedTemplateValue="edited value" />
      </Provider>,
    );

    const fields = screen.getAllByRole('textbox');
    expect(fields).toHaveLength(2);
    expect(fields[0]).toHaveValue('edited name');
    expect(fields[1]).toHaveValue('edited value');
  });
});
