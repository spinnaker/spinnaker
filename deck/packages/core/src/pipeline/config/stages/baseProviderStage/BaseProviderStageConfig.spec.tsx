import { render, screen } from '@testing-library/react';
import { setupUser } from '../../../../utils/testUtils/userEvent';
import React from 'react';

import { CloudProviderRegistry } from '../../../../cloudProvider';
import { BaseProviderStageConfig } from './BaseProviderStageConfig';

describe('BaseProviderStageConfig', () => {
  // CloudProviderLabel resolves the display name from CloudProviderRegistry. The 'ecs' provider's
  // name is registered by the ecs package (loaded globally under the old Karma bundle); register it
  // here so this core-only spec exercises the same label without depending on that package.
  beforeEach(() => {
    CloudProviderRegistry.registerProvider('ecs', { name: 'EC2 Container Service' } as any);
  });

  it('renders nothing when no providers are available', () => {
    const { container } = render(
      <BaseProviderStageConfig providers={[]} readOnly={false} onProviderChange={vi.fn()} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders and selects the only provider once from an effect', () => {
    const onProviderChange = vi.fn();
    const providers = ['ecs'];
    const { rerender } = render(
      <BaseProviderStageConfig providers={providers} readOnly={false} onProviderChange={onProviderChange} />,
    );

    rerender(<BaseProviderStageConfig providers={providers} readOnly={false} onProviderChange={onProviderChange} />);

    expect(screen.getByText('EC2 Container Service')).toBeVisible();
    expect(onProviderChange).toHaveBeenCalledTimes(1);
    expect(onProviderChange).toHaveBeenCalledWith('ecs');
  });

  it('auto-selects the same sole provider again when a controlled parent changes stages and clears selection', () => {
    const onProviderChange = vi.fn();

    const ControlledSelector = ({ stageId }: { stageId: string }) => {
      const [selectedProvider, setSelectedProvider] = React.useState<string>();
      React.useEffect(() => setSelectedProvider(undefined), [stageId]);
      return (
        <BaseProviderStageConfig
          providers={['ecs']}
          selectedProvider={selectedProvider}
          readOnly={false}
          onProviderChange={(provider) => {
            onProviderChange(stageId, provider);
            setSelectedProvider(provider);
          }}
        />
      );
    };

    const { rerender } = render(<ControlledSelector stageId="1" />);

    expect(onProviderChange).toHaveBeenCalledTimes(1);
    expect(onProviderChange).toHaveBeenCalledWith('1', 'ecs');

    rerender(<ControlledSelector stageId="2" />);

    expect(onProviderChange).toHaveBeenCalledTimes(2);
    expect(onProviderChange).toHaveBeenCalledWith('2', 'ecs');
  });

  it('renders an editable provider select and emits its selected value', async () => {
    const user = setupUser();
    const onProviderChange = vi.fn();
    render(<BaseProviderStageConfig providers={['aws', 'ecs']} readOnly={false} onProviderChange={onProviderChange} />);

    const select = screen.getByRole('combobox');
    await user.click(select);
    expect(screen.getByRole('option', { name: 'aws' })).toBeVisible();
    await user.click(screen.getByRole('option', { name: 'ecs' }));

    expect(onProviderChange).toHaveBeenCalledExactlyOnceWith('ecs');
  });

  it('renders the selected provider without an editable select when read-only', () => {
    render(
      <BaseProviderStageConfig
        providers={['aws', 'ecs']}
        selectedProvider="ecs"
        readOnly={true}
        onProviderChange={vi.fn()}
      />,
    );

    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    expect(screen.getByText('EC2 Container Service')).toBeVisible();
  });

  it('does not auto-select a sole provider when read-only', () => {
    const onProviderChange = vi.fn();
    render(<BaseProviderStageConfig providers={['ecs']} readOnly={true} onProviderChange={onProviderChange} />);

    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    expect(screen.getByText('EC2 Container Service')).toBeVisible();
    expect(onProviderChange).not.toHaveBeenCalled();
  });
});
