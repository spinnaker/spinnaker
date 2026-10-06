import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { FormikProps } from 'formik/dist/types';
import React from 'react';

import { FormikFormField, FormikSpelContextProvider, ReactSelectInput, SpinFormik, TextInput } from '../..';

describe('<FormikFormField/>', () => {
  it('renders an input with the field name and nested value', async () => {
    render(
      <Test
        initialValues={{ foo: { bar: 'abc123' } }}
        render={() => <FormikFormField name="foo.bar" input={(props) => <TextInput {...props} />} />}
      />,
    );

    expect(await screen.findByRole('textbox')).toHaveAttribute('name', 'foo.bar');
    expect(screen.getByRole('textbox')).toHaveValue('abc123');
  });

  it('renders validation information for the field', async () => {
    render(
      <Test
        validate={() => ({ foo: { bar: 'bad' } })}
        initialValues={{ foo: { bar: 'abc123' } }}
        render={() => <FormikFormField name="foo.bar" input={(props) => <TextInput {...props} />} />}
      />,
    );

    const message = await screen.findByText('bad');
    expect(message.closest('.ValidationMessage')).toHaveClass('errorMessage');
    expect(screen.getByRole('textbox')).toHaveClass('dirty', 'invalid');
  });

  it('updates field validation when Formik field props are rerendered', async () => {
    const field = (validationMessage: string) => (
      <Test
        initialValues={{ name: 'value' }}
        render={() => (
          <FormikFormField
            name="name"
            input={(props) => <TextInput {...props} />}
            touched={true}
            validationMessage={validationMessage}
          />
        )}
      />
    );
    const { rerender } = render(field('first error'));
    expect(await screen.findByText('first error')).toBeInTheDocument();

    rerender(field('updated error'));

    expect(await screen.findByText('updated error')).toBeInTheDocument();
    expect(screen.queryByText('first error')).not.toBeInTheDocument();
  });

  it('does not index into string errors', async () => {
    render(
      <Test
        validate={() => ({ foo: { bar: 'bad' } })}
        initialValues={{ foo: { bar: ['abc'] } }}
        render={() => <FormikFormField name="foo.bar[0]" input={(props) => <TextInput {...props} />} />}
      />,
    );

    expect(await screen.findByRole('textbox')).toHaveValue('abc');
    expect(screen.queryByText('b')).not.toBeInTheDocument();
    expect(screen.queryByText('bad')).not.toBeInTheDocument();
  });

  describe('SpEL-awareness', () => {
    const AccountField = ({ spelAware }: { spelAware?: boolean }) => (
      <FormikFormField
        name="account"
        label="Account"
        input={(props) => <ReactSelectInput {...props} />}
        spelAware={spelAware}
      />
    );

    it('renders the SpEL toggle based on context and props', async () => {
      const configs: Array<Partial<ITestFormWrapperProps> & { expected: boolean }> = [
        { propsSpelAware: false, expected: false },
        { contextSpelAware: false, expected: false },
        { contextSpelAware: true, propsSpelAware: false, expected: false },
        { propsSpelAware: true, expected: true },
        { contextSpelAware: true, expected: true },
        { contextSpelAware: false, propsSpelAware: true, expected: true },
      ];

      for (const config of configs) {
        const { unmount } = render(
          <Test
            contextSpelAware={config.contextSpelAware}
            propsSpelAware={config.propsSpelAware}
            render={() => <AccountField spelAware={config.propsSpelAware} />}
          />,
        );
        await screen.findByRole('combobox');
        expect(screen.queryByRole('button', { name: 'Enter SpEL in freeform input' }) !== null).toBe(config.expected);
        unmount();
        cleanup();
      }
    });

    it('renders a freeform input initially when the field value is SpEL', async () => {
      render(<Test initialValues={{ account: '${spel_account}' }} render={() => <AccountField spelAware={true} />} />);

      expect(await screen.findByRole('textbox')).toHaveValue('${spel_account}');
      expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    });

    it('does not render the default input when the initial field value is SpEL', async () => {
      const defaultInput = vi.fn(() => <span>default input</span>);
      render(
        <Test
          initialValues={{ account: '${spel_account}' }}
          render={() => <FormikFormField name="account" label="Account" input={defaultInput} spelAware={true} />}
        />,
      );

      expect(await screen.findByRole('textbox')).toBeInTheDocument();
      expect(defaultInput).not.toHaveBeenCalled();
    });

    it('renders the default input if the field value is not SpEL', async () => {
      render(<Test initialValues={{ account: 'account' }} render={() => <AccountField spelAware={true} />} />);

      expect(await screen.findByRole('combobox')).toBeInTheDocument();
      expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    });

    it('switches from the default input to freeform and clears the value', async () => {
      render(<Test initialValues={{ account: 'my-account' }} render={() => <AccountField spelAware={true} />} />);
      await screen.findByRole('combobox');

      await userEvent.click(screen.getByRole('button', { name: 'Enter SpEL in freeform input' }));

      expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
      expect(screen.getByRole('textbox')).toHaveValue('');
    });

    it('switches from freeform to the default input and clears the value', async () => {
      render(<Test initialValues={{ account: '${spel_account}' }} render={() => <AccountField spelAware={true} />} />);
      expect(await screen.findByRole('textbox')).toHaveValue('${spel_account}');

      await userEvent.click(screen.getByRole('button', { name: 'Return to default input' }));

      expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
      expect(screen.getByRole('combobox')).toBeInTheDocument();
    });
  });
});

interface ITestFormWrapperProps {
  render: (props: FormikProps<any>) => React.ReactNode;
  validate?: (form: any) => any;
  initialValues?: any;
  contextSpelAware?: boolean;
  propsSpelAware?: boolean;
}

function Test(props: ITestFormWrapperProps) {
  return (
    <FormikSpelContextProvider value={props.contextSpelAware}>
      <SpinFormik
        initialValues={props.initialValues || {}}
        validate={props.validate || (() => ({}))}
        onSubmit={() => {}}
        render={props.render}
      />
    </FormikSpelContextProvider>
  );
}
