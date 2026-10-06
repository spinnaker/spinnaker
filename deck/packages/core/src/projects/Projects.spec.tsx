import { screen, within } from '@testing-library/react';
import { setupUser } from '../utils/testUtils/userEvent';
import * as React from 'react';
import type { Mock } from 'vitest';

import { Projects } from './Projects';
import { DeckRuntimeContext } from '../bootstrap/DeckRuntimeContext';
import { ViewStateCache } from '../cache';
import * as ProjectReaderModule from './service/ProjectReader';
import { timestamp } from '../utils';
import { renderWithRouter } from '../utils/testUtils/rtl';

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

const getRenderedRows = () => screen.getAllByRole('row').slice(1);
const getRenderedNames = () => getRenderedRows().map((row) => within(row).getAllByRole('cell')[0].textContent ?? '');

describe('Projects', () => {
  let listSpy: Mock;

  const renderProjects = () =>
    renderWithRouter(
      <DeckRuntimeContext.Provider
        value={{ services: { cacheInitializer: {} } } as React.ContextType<typeof DeckRuntimeContext>}
      >
        <Projects />
      </DeckRuntimeContext.Provider>,
    );

  describe('filtering & sorting', () => {
    beforeEach(() => {
      listSpy = vi.spyOn(ProjectReaderModule.ProjectReader, 'listProjects').mockResolvedValue(projectList);
    });

    afterEach(() => {
      ViewStateCache.clearCache('projects');
    });

    it('sets loaded flag and renders projects sorted by name asc', async () => {
      renderProjects();

      expect(await screen.findByText('deck')).toBeInTheDocument();
      expect(getRenderedRows()).toHaveLength(3);
      expect(getRenderedNames()).toEqual(['deck', 'mort', 'oort']);

      const firstRowCells = within(getRenderedRows()[0]).getAllByRole('cell');
      expect(firstRowCells[1]).toHaveTextContent(timestamp(new Date(2).getTime()));
      expect(firstRowCells[2]).toHaveTextContent(timestamp(new Date(2).getTime()));
      expect(firstRowCells[3]).toHaveTextContent('a@netflix.com');
      expect(listSpy).toHaveBeenCalledTimes(1);
    });

    it('filters by name or email as the user types', async () => {
      const user = setupUser();
      renderProjects();
      const input = await screen.findByPlaceholderText('Search projects');
      await screen.findByText('deck');

      await user.type(input, 'a@netflix.com');
      expect(getRenderedNames()).toEqual(['deck']);

      await user.clear(input);
      await user.type(input, 'ort');
      expect(getRenderedNames()).toEqual(['mort', 'oort']);

      await user.clear(input);
      expect(getRenderedRows()).toHaveLength(3);
    });

    it('sorts by -name, -createTs, createTs, and combines with a filter', async () => {
      const user = setupUser();
      renderProjects();
      await screen.findByText('deck');

      await user.click(screen.getByText('Name', { selector: '.sort-toggle' }));
      expect(getRenderedNames()).toEqual(['oort', 'mort', 'deck']);

      const createdToggle = screen.getByText('Created', { selector: '.sort-toggle' });
      await user.click(createdToggle);
      expect(getRenderedNames()).toEqual(['oort', 'deck', 'mort']);

      await user.click(createdToggle);
      expect(getRenderedNames()).toEqual(['mort', 'deck', 'oort']);

      await user.type(screen.getByPlaceholderText('Search projects'), 'ort');
      expect(getRenderedNames()).toEqual(['mort', 'oort']);
    });
  });
});
