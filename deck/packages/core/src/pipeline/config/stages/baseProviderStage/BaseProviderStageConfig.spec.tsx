import { mount } from 'enzyme';
import React from 'react';

import { CloudProviderLabel, CloudProviderLogo } from '../../../../cloudProvider';
import { ReactSelectInput } from '../../../../presentation';
import { BaseProviderStageConfig } from './BaseProviderStageConfig';

describe('BaseProviderStageConfig', () => {
  it('renders nothing when no providers are available', () => {
    const wrapper = mount(
      <BaseProviderStageConfig providers={[]} readOnly={false} onProviderChange={jasmine.createSpy()} />,
    );

    expect(wrapper.isEmptyRender()).toBe(true);

    wrapper.unmount();
  });

  it('renders and selects the only provider once from an effect', () => {
    const onProviderChange = jasmine.createSpy('onProviderChange');
    const providers = ['ecs'];
    const wrapper = mount(
      <BaseProviderStageConfig providers={providers} readOnly={false} onProviderChange={onProviderChange} />,
    );

    wrapper.setProps({ providers });
    wrapper.update();

    expect(wrapper.find(CloudProviderLogo).prop('provider')).toBe('ecs');
    expect(wrapper.find(CloudProviderLabel).prop('provider')).toBe('ecs');
    expect(wrapper.find('.base-provider-label').text()).toBe('EC2 Container Service');
    expect(onProviderChange).toHaveBeenCalledTimes(1);
    expect(onProviderChange).toHaveBeenCalledWith('ecs');

    wrapper.unmount();
  });

  it('auto-selects the same sole provider again when a controlled parent changes stages and clears selection', () => {
    const onProviderChange = jasmine.createSpy('onProviderChange');

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

    const wrapper = mount(<ControlledSelector stageId="1" />);

    expect(onProviderChange).toHaveBeenCalledTimes(1);
    expect(onProviderChange).toHaveBeenCalledWith('1', 'ecs');

    wrapper.setProps({ stageId: '2' });
    wrapper.update();

    expect(onProviderChange).toHaveBeenCalledTimes(2);
    expect(onProviderChange).toHaveBeenCalledWith('2', 'ecs');

    wrapper.unmount();
  });

  it('renders an editable provider select and emits its selected value', () => {
    const onProviderChange = jasmine.createSpy('onProviderChange');
    const wrapper = mount(
      <BaseProviderStageConfig providers={['aws', 'ecs']} readOnly={false} onProviderChange={onProviderChange} />,
    );

    const select = wrapper.find(ReactSelectInput);
    expect(select.prop('name')).toBe('cloudProviderType');
    expect(select.prop('options')).toEqual([
      { label: 'aws', value: 'aws' },
      { label: 'ecs', value: 'ecs' },
    ]);

    select.prop('onChange')({ target: { value: 'ecs' } } as any);

    expect(onProviderChange).toHaveBeenCalledOnceWith('ecs');

    wrapper.unmount();
  });

  it('renders the selected provider without an editable select when read-only', () => {
    const wrapper = mount(
      <BaseProviderStageConfig
        providers={['aws', 'ecs']}
        selectedProvider="ecs"
        readOnly={true}
        onProviderChange={jasmine.createSpy()}
      />,
    );

    expect(wrapper.find(ReactSelectInput).exists()).toBe(false);
    expect(wrapper.find(CloudProviderLogo).prop('provider')).toBe('ecs');
    expect(wrapper.find(CloudProviderLabel).prop('provider')).toBe('ecs');

    wrapper.unmount();
  });

  it('does not auto-select a sole provider when read-only', () => {
    const onProviderChange = jasmine.createSpy('onProviderChange');
    const wrapper = mount(
      <BaseProviderStageConfig providers={['ecs']} readOnly={true} onProviderChange={onProviderChange} />,
    );

    expect(wrapper.find(ReactSelectInput).exists()).toBe(false);
    expect(wrapper.find(CloudProviderLogo).prop('provider')).toBe('ecs');
    expect(onProviderChange).not.toHaveBeenCalled();

    wrapper.unmount();
  });
});
