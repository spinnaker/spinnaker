import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import { CollapsibleSectionStateCache } from '../../cache';
import { UrlBuilder } from '../../navigation';
import { ProjectCluster } from './ProjectCluster';
import { RegionFilter } from './RegionFilter';

describe('<ProjectCluster />', () => {
  const project = { name: 'kubernetesproject' } as any;

  const cluster = {
    account: 'k8s-local',
    stack: '*',
    detail: '*',
    instanceCounts: { total: 24, up: 24, down: 0, unknown: 0, outOfService: 0, starting: 0 },
    applications: [
      {
        application: 'kubernetesapp',
        lastPush: Date.now() - 60_000,
        clusters: [
          {
            region: 'dev',
            builds: [{ buildNumber: '0', images: ['nginx'] }],
            instanceCounts: { total: 8, up: 8, down: 0, unknown: 0, outOfService: 0, starting: 0 },
          },
          {
            region: 'prod',
            builds: [{ buildNumber: '0', images: ['nginx'] }],
            instanceCounts: { total: 8, up: 8, down: 0, unknown: 0, outOfService: 0, starting: 0 },
          },
          {
            region: 'test',
            builds: [{ buildNumber: '0', images: ['nginx'] }],
            instanceCounts: { total: 8, up: 8, down: 0, unknown: 0, outOfService: 0, starting: 0 },
          },
        ],
      },
    ],
  } as any;

  beforeEach(() => {
    spyOn(CollapsibleSectionStateCache, 'isSet').and.returnValue(false);
    spyOn(CollapsibleSectionStateCache, 'setExpanded').and.stub();
    spyOn(UrlBuilder, 'buildFromMetadata').and.callFake((metadata: any) => {
      const query = [`acct=${metadata.account}`];
      if (metadata.region) {
        query.push(`reg=${metadata.region}`);
      }
      return `#/projects/${metadata.project}/applications/${metadata.application}/clusters?${query.join('&')}`;
    });
  });

  it('renders the legacy project-cluster rollup DOM contract', () => {
    const wrapper = mount(<ProjectCluster project={project} cluster={cluster} selectedRegions={{}} />);

    expect(wrapper.find('project-cluster .rollup-entry').exists()).toBe(true);
    expect(wrapper.find('.cluster-name').text()).toContain('*-*');
    expect(wrapper.find('.cluster-health').at(0).text()).toContain('1 Application');
    expect(wrapper.find('.cluster-health').at(1).text()).toContain('24 Instances');
    expect(wrapper.find('.rollup-details thead th').map((th) => th.text().trim())).toEqual([
      '',
      '',
      'Last Push',
      'dev',
      'prod',
      'test',
    ]);
    expect(wrapper.find('tbody tr').first().find('a.heavy').text()).toContain('KUBERNETESAPP');
    expect(wrapper.find('tbody tr').first().find('td a[href*="reg="]').length).toBe(3);

    wrapper.unmount();
  });

  it('filters region columns and links', () => {
    const wrapper = mount(
      <ProjectCluster project={project} cluster={cluster} selectedRegions={{ dev: true, prod: true }} />,
    );

    expect(wrapper.find('.rollup-details thead th').map((th) => th.text().trim())).toEqual([
      '',
      '',
      'Last Push',
      'dev',
      'prod',
    ]);
    expect(wrapper.find('tbody tr').first().find('td a[href*="reg="]').length).toBe(2);

    wrapper.unmount();
  });

  it('toggles details and persists expansion state', () => {
    const wrapper = mount(<ProjectCluster project={project} cluster={cluster} selectedRegions={{}} />);

    expect(wrapper.find('.rollup-details').exists()).toBe(true);
    wrapper.find('.rollup-entry .row.clickable').simulate('click');

    expect(wrapper.find('.rollup-details').exists()).toBe(false);
    expect(CollapsibleSectionStateCache.setExpanded).toHaveBeenCalledWith('kubernetesproject:k8s-local:*', false);

    wrapper.unmount();
  });
});

describe('<RegionFilter />', () => {
  it('renders region checkboxes and exposes toggle/clear actions', () => {
    const onToggleRegion = jasmine.createSpy('onToggleRegion');
    const onClear = jasmine.createSpy('onClear');
    const wrapper = mount(
      <RegionFilter
        regions={['dev', 'prod']}
        selectedRegions={{ dev: true }}
        onToggleRegion={onToggleRegion}
        onClear={onClear}
      />,
    );

    wrapper.find('h6.dropdown-toggle').simulate('click');

    expect(wrapper.find('.region-filter-button').text()).toContain('Filter by region / namespace');
    expect(wrapper.find('input[type="checkbox"]').at(0).prop('checked')).toBe(true);
    wrapper.find('li').at(1).simulate('click');
    expect(onToggleRegion).toHaveBeenCalledWith('prod');
    wrapper.find('a').last().simulate('click');
    expect(onClear).toHaveBeenCalled();

    wrapper.unmount();
  });

  it('closes the dropdown when clicking outside', () => {
    const wrapper = mount(
      <RegionFilter
        regions={['dev', 'prod']}
        selectedRegions={{}}
        onToggleRegion={jasmine.createSpy()}
        onClear={jasmine.createSpy()}
      />,
    );

    wrapper.find('h6.dropdown-toggle').simulate('click');
    expect(wrapper.find('.dropdown-menu').exists()).toBe(true);

    act(() => {
      document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    wrapper.update();

    expect(wrapper.find('.dropdown-menu').exists()).toBe(false);

    wrapper.unmount();
  });
});
