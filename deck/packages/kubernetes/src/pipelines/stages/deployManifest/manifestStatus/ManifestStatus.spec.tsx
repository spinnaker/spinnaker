import { shallow } from 'enzyme';
import React from 'react';

import { ManifestStatus } from './ManifestStatus';

describe('<ManifestStatus />', () => {
  it('should render the manifest kind and name in the heading', () => {
    const manifest = {
      manifest: {
        kind: 'ConfigMap',
        metadata: {
          name: 'my-configmap',
        },
      },
    };

    const wrapper = shallow(<ManifestStatus manifest={manifest as any} account="my-account" />);

    expect(wrapper.find('dt').text()).toBe('ConfigMap my-configmap');
  });

  it('should distinguish between multiple manifests of the same kind', () => {
    const first = {
      manifest: {
        kind: 'ConfigMap',
        metadata: {
          name: 'first-configmap',
        },
      },
    };
    const second = {
      manifest: {
        kind: 'ConfigMap',
        metadata: {
          name: 'second-configmap',
        },
      },
    };

    const firstWrapper = shallow(<ManifestStatus manifest={first as any} account="my-account" />);
    const secondWrapper = shallow(<ManifestStatus manifest={second as any} account="my-account" />);

    expect(firstWrapper.find('dt').text()).toBe('ConfigMap first-configmap');
    expect(secondWrapper.find('dt').text()).toBe('ConfigMap second-configmap');
    expect(firstWrapper.find('dt').text()).not.toBe(secondWrapper.find('dt').text());
  });
});
