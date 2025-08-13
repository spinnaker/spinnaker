import * as React from 'react';
import { ReactWrapper } from 'enzyme';
import { act } from 'react-dom/test-utils';

import type { UIRouterReact } from '@uirouter/react';
import { mock } from 'angular';
import { REACT_MODULE } from '../reactShims';
import { OVERRIDE_REGISTRY } from '../overrideRegistry';

import { Projects } from './Projects';
import * as ProjectReaderModule from './service/ProjectReader';
import { mountAndFlush } from '../utils/testUtils';
import { ViewStateCache } from '../cache';
import { timestamp } from '../utils';

type TestProject = ReturnType<typeof makeProject>;

const makeProject = (name: string, email: string, createTs: number, updateTs: number) => ({
  id: name,
  name,
  email,
  createTs,
  updateTs,
  config: { pipelineConfigs: [], applications: [], clusters: [] },
  lastModifiedBy: 'anonymous',
});

const deck = makeProject('deck', 'a@netflix.com', new Date(2).getTime(), new Date(2).getTime());
const oort = makeProject('oort', 'b@netflix.com', new Date(3).getTime(), new Date(3).getTime());
const mort = makeProject('mort', 'c@netflix.com', new Date(1).getTime(), new Date(1).getTime());
const projectList: TestProject[] = [deck, oort, mort];

const getRenderedNames = (wrapper: ReactWrapper) => wrapper.find('tbody tr').map((r) => r.find('td a').first().text());

export function invokeSort(toggle: ReactWrapper<any>, next: string) {
  const onChange = toggle.prop('onChange') as (v: string) => void;
  onChange(next);
}

describe('Projects', () => {
  let $uiRouter: UIRouterReact;
  let listSpy: jasmine.Spy;

  beforeEach(mock.module(REACT_MODULE, OVERRIDE_REGISTRY));
  beforeEach(
    mock.inject((_$uiRouter_: UIRouterReact) => {
      $uiRouter = _$uiRouter_;
    }),
  );

  describe('filtering & sorting', () => {
    beforeEach(() => {
      listSpy = spyOn(ProjectReaderModule.ProjectReader, 'listProjects').and.returnValue(Promise.resolve(projectList));
    });

    afterEach(() => {
      ViewStateCache.clearCache('projects');
    });

    it('sets loaded flag and renders projects sorted by name asc', async () => {
      const wrapper = await mountAndFlush(<Projects />);

      const rows = wrapper.find('tbody tr');
      expect(rows.length).toBe(3);

      expect(getRenderedNames(wrapper)).toEqual(['deck', 'mort', 'oort']);

      const firstRowTds = rows.at(0).find('td');
      expect(firstRowTds.at(1).text()).toContain(timestamp(new Date(2).getTime())); // createTs
      expect(firstRowTds.at(2).text()).toContain(timestamp(new Date(2).getTime())); // updateTs
      expect(firstRowTds.at(3).text()).toBe('a@netflix.com');
    });

    it('filters by name or email as the user types', async () => {
      const wrapper = await mountAndFlush(<Projects />);

      const input = wrapper.find('input[placeholder="Search projects"]');
      expect(input.exists()).toBeTrue();

      // Filter by email
      await act(async () => {
        input.prop('onChange')!({ target: { value: 'a@netflix.com' } } as any);
      });
      wrapper.update();
      let rows = wrapper.find('tbody tr');
      expect(rows.length).toBe(1);
      expect(rows.at(0).find('td a').text()).toBe('deck');

      // Filter by substring 'ort'
      await act(async () => {
        input.prop('onChange')!({ target: { value: 'ort' } } as any);
      });
      wrapper.update();
      rows = wrapper.find('tbody tr');
      expect(rows.map((r) => r.find('td a').text())).toEqual(['mort', 'oort']);

      // Clear
      await act(async () => {
        input.prop('onChange')!({ target: { value: '' } } as any);
      });
      wrapper.update();
      expect(wrapper.find('tbody tr').length).toBe(3);
    });

    it('sorts by -name, -createTs, createTs, and combines with a filter', async () => {
      const wrapper = await mountAndFlush(<Projects />);

      const sortToggles = wrapper.find('SortToggle');

      // -name (desc)
      const nameToggle = sortToggles.filterWhere((n) => n.prop('label') === 'Name').first();
      await act(async () => {
        invokeSort(nameToggle, '-name');
      });
      wrapper.update();
      expect(getRenderedNames(wrapper)).toEqual(['oort', 'mort', 'deck']);

      // -createTs (desc)
      const createdToggle = sortToggles.filterWhere((n) => n.prop('label') === 'Created').first();
      await act(async () => {
        invokeSort(createdToggle, '-createTs');
      });
      wrapper.update();
      expect(getRenderedNames(wrapper)).toEqual(['oort', 'deck', 'mort']);

      // -createTs (asc)
      await act(async () => {
        invokeSort(createdToggle, 'createTs');
      });
      wrapper.update();
      expect(getRenderedNames(wrapper)).toEqual(['mort', 'deck', 'oort']);

      // Add filter ("ort") while sorted by createTs
      const input = wrapper.find('input[placeholder="Search projects"]');
      await act(async () => {
        input.prop('onChange')!({ target: { value: 'ort' } } as any);
      });
      wrapper.update();
      expect(getRenderedNames(wrapper)).toEqual(['mort', 'oort']);
    });
  });
});
