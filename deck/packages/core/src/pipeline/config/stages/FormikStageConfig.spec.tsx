import { cleanup, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { FormikStageConfig } from './FormikStageConfig';
import type { IStage } from '../../../domain';
import { FormikFormField, TextInput } from '../../../presentation';
import { setupUser } from '../../../utils/testUtils/userEvent';

describe('FormikStageConfig', () => {
  const config = (stage: IStage, onChange: (values: IStage) => void) => (
    <FormikStageConfig
      application={{} as any}
      pipeline={{} as any}
      stage={stage}
      onChange={onChange}
      render={({ formik }) => (
        <>
          <FormikFormField name="command" input={(inputProps) => <TextInput {...inputProps} />} />
          <button type="button" onClick={() => formik.setFieldValue('command', undefined)}>
            Remove command
          </button>
        </>
      )}
    />
  );

  const typeCommand = async (user: ReturnType<typeof setupUser>, value: string) => {
    const input = screen.getByRole('textbox');
    await user.clear(input);
    await user.type(input, value);
  };

  afterEach(cleanup);

  it('keeps stage fields changed outside the form', async () => {
    const user = setupUser();
    const stage: IStage = {
      refId: '1',
      name: 'Script',
      type: 'script',
      requisiteStageRefIds: [],
      command: 'a',
      failPipeline: true,
    };
    // Applies the values to the stage, as the pipeline editor does
    const onChange = (values: IStage) => Object.assign(stage, values);
    const { rerender } = render(config(stage, onChange));

    await typeCommand(user, 'b');
    // Like Execution Options, which edits the stage outside the form
    stage.failPipeline = false;
    rerender(config(stage, onChange));
    await typeCommand(user, 'c');

    await waitFor(() => expect(stage.command).toBe('c'));
    expect(stage.failPipeline).toBe(false);
  });

  it('passes the whole stage to onChange', async () => {
    const user = setupUser();
    const stage: IStage = {
      refId: '1',
      name: 'Script',
      type: 'script',
      requisiteStageRefIds: [],
      command: 'a',
      failPipeline: true,
    };
    const onChange = vi.fn();
    render(config(stage, onChange));

    await typeCommand(user, 'b');

    await waitFor(() =>
      expect(onChange).toHaveBeenLastCalledWith({
        refId: '1',
        name: 'Script',
        type: 'script',
        requisiteStageRefIds: [],
        command: 'b',
        failPipeline: true,
      }),
    );
  });

  it('clears a stage field the form removes', async () => {
    const user = setupUser();
    const stage: IStage = { refId: '1', name: 'Script', type: 'script', requisiteStageRefIds: [], command: 'a' };
    const onChange = (values: IStage) => Object.assign(stage, values);
    render(config(stage, onChange));

    await user.click(screen.getByRole('button', { name: 'Remove command' }));

    await waitFor(() => expect(stage.command).toBeUndefined());
  });
});
