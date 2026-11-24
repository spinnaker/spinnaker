import { UIRouterReact } from '@uirouter/react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { createDeckRuntime } from '../../bootstrap/DeckRuntime';
import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import { SpelText } from './SpelText';

describe('SpelText', () => {
  it('unmounts safely while autocomplete setup is still pending', async () => {
    const runtime = createDeckRuntime(new UIRouterReact());
    let resolveExecution: (execution: any) => void;
    vi.spyOn(runtime.services.executionService, 'getLastExecutionForApplicationByConfigId').mockReturnValue(
      new Promise((resolve) => (resolveExecution = resolve)) as any,
    );
    const { unmount } = render(
      <DeckRuntimeContext.Provider value={runtime}>
        <SpelText
          placeholder="Expression"
          value=""
          onChange={() => undefined}
          pipeline={{ id: 'pipeline-id', application: 'app' } as any}
          docLink={false}
        />
      </DeckRuntimeContext.Provider>,
    );

    unmount();
    resolveExecution(null);
    await Promise.resolve();

    runtime.dispose();
  });

  it('uses runtime execution data for visible autocomplete suggestions', async () => {
    const runtime = createDeckRuntime(new UIRouterReact());
    const getLastExecution = vi
      .spyOn(runtime.services.executionService, 'getLastExecutionForApplicationByConfigId')
      .mockResolvedValue({
        id: 'execution-id',
        stages: [],
        context: { deploymentDetails: { region: 'us-east-1' } },
      } as any);
    const Harness = () => {
      const [value, setValue] = React.useState('');
      return (
        <DeckRuntimeContext.Provider value={runtime}>
          <SpelText
            placeholder="Expression"
            value={value}
            onChange={setValue}
            pipeline={{ id: 'pipeline-id', application: 'app', stages: [] } as any}
            docLink={false}
          />
        </DeckRuntimeContext.Provider>
      );
    };
    render(<Harness />);
    const input = screen.getByPlaceholderText('Expression');
    await waitFor(() => expect(getLastExecution).toHaveBeenCalledWith('app', 'pipeline-id'));
    await Promise.resolve();
    await Promise.resolve();

    await userEvent.type(input, 'deployedServerGroups');

    expect(await screen.findByText(/region/)).toBeInTheDocument();
    runtime.dispose();
  });
});
