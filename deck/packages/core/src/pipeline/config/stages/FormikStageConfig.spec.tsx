import { cleanup, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { FormikStageConfig } from './FormikStageConfig';
import type { IStage } from '../../../domain';
import { FormikFormField, TextInput } from '../../../presentation';
import { setupUser } from '../../../utils/testUtils/userEvent';

describe('FormikStageConfig', () => {
  const config = (stage: IStage, onChange: (values: Partial<IStage>) => void) => (
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
    const onChange = (values: Partial<IStage>) => Object.assign(stage, values);
    const { rerender } = render(config(stage, onChange));

    await typeCommand(user, 'b');
    // Like Execution Options, which edits the stage outside the form
    stage.failPipeline = false;
    rerender(config(stage, onChange));
    await typeCommand(user, 'c');

    await waitFor(() => expect(stage.command).toBe('c'));
    expect(stage.failPipeline).toBe(false);
  });

  it('does not write back values from a stale stage copy', async () => {
    const user = setupUser();
    const liveStage: IStage = { refId: '1', name: 'Script', type: 'script', requisiteStageRefIds: [], mode: 'text' };
    // Like stage configs that memoize a copy of the stage with defaults applied
    const staleCopy = { ...liveStage };
    const onChange = (values: Partial<IStage>) => Object.assign(liveStage, values);
    render(config(staleCopy, onChange));

    liveStage.mode = 'trigger';
    await typeCommand(user, 'b');

    await waitFor(() => expect(liveStage.command).toBe('b'));
    expect(liveStage.mode).toBe('trigger');
  });

  it('passes only the changed fields to onChange', async () => {
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

    await waitFor(() => expect(onChange).toHaveBeenLastCalledWith({ command: 'b' }));
  });

  it('clears a stage field the form removes', async () => {
    const user = setupUser();
    const stage: IStage = { refId: '1', name: 'Script', type: 'script', requisiteStageRefIds: [], command: 'a' };
    const onChange = (values: Partial<IStage>) => Object.assign(stage, values);
    render(config(stage, onChange));

    await user.click(screen.getByRole('button', { name: 'Remove command' }));

    await waitFor(() => expect(stage.command).toBeUndefined());
  });
});
