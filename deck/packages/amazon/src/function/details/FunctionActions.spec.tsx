import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import type { Application } from '@spinnaker/core';
import { ConfirmationModalService, DeckRuntimeContext, FunctionWriter } from '@spinnaker/core';

import { AWSProviderSettings } from '../../aws.settings';
import type { IAmazonFunction } from '../../index';
import { CreateLambdaFunction, FunctionActions } from '../../index';

describe('FunctionActions', () => {
  const originalAdHocInfraWritesEnabled = AWSProviderSettings.adHocInfraWritesEnabled;
  const app = ({ functions: { refresh: vi.fn() }, name: 'app' } as any) as Application;
  const functionDef = {
    cloudProvider: 'aws',
    credentials: 'test-account',
    functionName: 'app-function',
    region: 'us-east-1',
  } as IAmazonFunction;
  const functionFromParams = {
    account: 'test-account',
    region: 'us-east-1',
    functionName: 'function-name',
  };
  const runtimeServices = {} as any;
  const renderActions = () =>
    render(
      <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>
        <FunctionActions app={app} functionDef={functionDef} functionFromParams={functionFromParams} />
      </DeckRuntimeContext.Provider>,
    );

  afterEach(() => {
    AWSProviderSettings.adHocInfraWritesEnabled = originalAdHocInfraWritesEnabled;
  });

  it('opens edit and delete actions with the exact function state and command', async () => {
    AWSProviderSettings.adHocInfraWritesEnabled = true;
    const show = vi.spyOn(CreateLambdaFunction, 'show').mockReturnValue(undefined);
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    const deleteFunction = vi.spyOn(FunctionWriter, 'deleteFunction').mockResolvedValue({} as any);
    renderActions();

    await userEvent.click(screen.getByRole('button', { name: 'Function Actions' }));
    await userEvent.click(screen.getByText('Edit Function'));
    expect(show).toHaveBeenCalledExactlyOnceWith({ app, functionDef }, runtimeServices);

    await userEvent.click(screen.getByText('Delete Function'));
    expect(confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        account: 'test-account',
        buttonText: 'Delete function-name',
        header: 'Really delete function-name in us-east-1: test-account?',
      }),
    );
    confirm.mock.lastCall[0].submitMethod();
    expect(deleteFunction).toHaveBeenCalledExactlyOnceWith(
      {
        cloudProvider: 'aws',
        credentials: 'test-account',
        functionName: 'app-function',
        region: 'us-east-1',
      },
      app,
    );
  });

  it('should not render DropdownToggle if aws.adHocInfraWritesEnabled is false', () => {
    AWSProviderSettings.adHocInfraWritesEnabled = false;
    renderActions();

    expect(screen.queryByRole('button', { name: 'Function Actions' })).not.toBeInTheDocument();
  });
});
