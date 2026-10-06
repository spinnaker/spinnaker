import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { CollapsibleSectionStateCache } from '../../cache';
import { UrlBuilder } from '../../navigation';
import { ProjectCluster } from './ProjectCluster';
import { RegionFilter } from './RegionFilter';
import type { IProjectClusterMetadata, IProjectDashboardCluster } from './ProjectClusterModel';
import type { IProject } from '../../domain';

describe('<ProjectCluster />', () => {
  const project = { name: 'kubernetesproject' } as IProject;

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
  } as IProjectDashboardCluster;

  beforeEach(() => {
    vi.spyOn(CollapsibleSectionStateCache, 'isSet').mockReturnValue(false);
    vi.spyOn(CollapsibleSectionStateCache, 'setExpanded').mockReturnValue(undefined);
    vi.spyOn(UrlBuilder, 'buildFromMetadata').mockImplementation((metadata: IProjectClusterMetadata) => {
      const query = [`acct=${metadata.account}`];
      if (metadata.region) {
        query.push(`reg=${metadata.region}`);
      }
      return `#/projects/${metadata.project}/applications/${metadata.application}/clusters?${query.join('&')}`;
    });
  });

  it('renders the project cluster rollup DOM contract', () => {
    const { container } = render(<ProjectCluster project={project} cluster={cluster} selectedRegions={{}} />);

    expect(container.querySelector('section.project-cluster .rollup-entry')).toBeInTheDocument();
    expect(screen.getByText('*-*')).toBeInTheDocument();
    expect(screen.getByText('1 Application')).toBeInTheDocument();
    expect(screen.getByText(/24 Instances/)).toBeInTheDocument();
    expect(screen.getAllByRole('columnheader').map((header) => header.textContent?.trim())).toEqual([
      '',
      '',
      'Last Push',
      'dev',
      'prod',
      'test',
    ]);
    const applicationRow = screen.getAllByRole('row')[1];
    expect(within(applicationRow).getByText('KUBERNETESAPP')).toBeInTheDocument();
    expect(
      within(applicationRow)
        .getAllByRole('link')
        .filter((link) => link.getAttribute('href')?.includes('reg=')),
    ).toHaveLength(3);
  });

  it('filters region columns and links', () => {
    render(<ProjectCluster project={project} cluster={cluster} selectedRegions={{ dev: true, prod: true }} />);

    expect(screen.getAllByRole('columnheader').map((header) => header.textContent?.trim())).toEqual([
      '',
      '',
      'Last Push',
      'dev',
      'prod',
    ]);
    const applicationRow = screen.getAllByRole('row')[1];
    expect(
      within(applicationRow)
        .getAllByRole('link')
        .filter((link) => link.getAttribute('href')?.includes('reg=')),
    ).toHaveLength(2);
  });

  it('toggles details and persists expansion state', async () => {
    const user = userEvent.setup();
    render(<ProjectCluster project={project} cluster={cluster} selectedRegions={{}} />);

    expect(screen.getByRole('table')).toBeInTheDocument();
    await user.click(screen.getByText('*-*'));

    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(CollapsibleSectionStateCache.setExpanded).toHaveBeenCalledWith('kubernetesproject:k8s-local:*', false);
  });
});

describe('<RegionFilter />', () => {
  it('renders region checkboxes and exposes toggle/clear actions', async () => {
    const user = userEvent.setup();
    const onToggleRegion = vi.fn();
    const onClear = vi.fn();
    render(
      <RegionFilter
        regions={['dev', 'prod']}
        selectedRegions={{ dev: true }}
        onToggleRegion={onToggleRegion}
        onClear={onClear}
      />,
    );

    await user.click(screen.getByText('Filter by region / namespace'));

    expect(screen.getAllByRole('checkbox')).toHaveLength(2);
    expect(screen.getAllByRole('checkbox')[0]).toBeChecked();
    await user.click(screen.getByText('prod'));
    expect(onToggleRegion).toHaveBeenCalledWith('prod');
    await user.click(screen.getByRole('link', { name: 'Clear all' }));
    expect(onClear).toHaveBeenCalledTimes(1);
  });

  it('closes the dropdown when clicking outside', async () => {
    const user = userEvent.setup();
    render(<RegionFilter regions={['dev', 'prod']} selectedRegions={{}} onToggleRegion={vi.fn()} onClear={vi.fn()} />);

    await user.click(screen.getByText('Filter by region / namespace'));
    expect(screen.getByRole('menu')).toBeInTheDocument();

    fireEvent.click(document.body);

    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });
});
