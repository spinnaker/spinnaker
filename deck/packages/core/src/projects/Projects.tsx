import { UISref } from '@uirouter/react';
import { orderBy } from 'lodash';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import type { SelectCallback } from 'react-bootstrap';

import { PaginationControls } from '../application/search/PaginationControls';
import { ViewStateCache } from '../cache';
import type { IProject } from '../domain';
import { InsightMenu } from '../insight/InsightMenu';
import { anyFieldFilter } from '../presentation/anyFieldFilter/anyField.filter';
import { SortToggle } from '../presentation';
import { ProjectReader } from './service/ProjectReader';
import { timestamp } from '../utils';
import { Spinner } from '../widgets';

interface IProjectSummary extends IProject {
  createTs?: number;
  updateTs?: number;
  email: string;
}

interface IProjectsViewState {
  projectFilter: string;
  sort: string;
}

export const Projects = () => {
  const cache = useRef(ViewStateCache.get('projects') || ViewStateCache.createCache('projects', { version: 1 }))
    .current;
  const cached: IProjectsViewState = cache.get('#global') || { projectFilter: '', sort: 'name' };

  const [projects, setProjects] = useState<IProjectSummary[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [projectFilter, setProjectFilter] = useState<string>(cached.projectFilter);
  const [sortKey, setSortKey] = useState<string>(cached.sort);
  const [currentPage, setCurrentPage] = useState(1);
  const inputRef = useRef<HTMLInputElement>();

  useEffect(() => {
    cache.put('#global', { projectFilter, sort: sortKey });
  }, [projectFilter, sortKey, cache]);

  useEffect(() => {
    ProjectReader.listProjects().then((result: IProjectSummary[]) => {
      setProjects(result);
      setLoaded(true);
    });
  }, []);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  useEffect(() => {
    setCurrentPage(1);
  }, [projectFilter, sortKey]);

  const filteredProjects = useMemo(() => {
    const filterFn = anyFieldFilter();
    const filtered = filterFn(projects, {
      name: projectFilter,
      email: projectFilter,
    });
    const key = sortKey?.startsWith('-') ? sortKey.slice(1) : sortKey;
    const direction = sortKey?.startsWith('-') ? 'desc' : 'asc';
    return orderBy(filtered, [key], [direction]);
  }, [projects, projectFilter, sortKey]);

  const itemsPerPage = 12;
  const totalPages = Math.ceil(filteredProjects.length / itemsPerPage) || 1;
  const pageProjects = filteredProjects.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);

  const changePage: SelectCallback = (page: any) => setCurrentPage(Number(page));

  const Loading = () => (
    <div className="horizontal center middle" style={{ marginBottom: '250px', height: '100px' }}>
      <Spinner size="small" />
    </div>
  );

  return (
    <div className="infrastructure">
      <div className="infrastructure-section search-header">
        <div className="container">
          <h2 className="header-section">
            <span className="search-label">Projects</span>
            <input
              type="search"
              placeholder="Search projects"
              className="form-control input-md"
              ref={inputRef}
              value={projectFilter}
              onChange={(e) => setProjectFilter(e.target.value)}
            />
          </h2>
          <div className="header-actions">
            <InsightMenu createApp={false} createProject={true} refreshCaches={false} />
          </div>
        </div>
      </div>
      <div className="container">
        {!loaded && <Loading />}
        {loaded && (
          <>
            <table className="table table-hover">
              <thead>
                <tr>
                  <th style={{ width: '20%' }}>
                    <SortToggle currentSort={sortKey} onChange={setSortKey} label="Name" sortKey="name" />
                  </th>
                  <th style={{ width: '20%' }}>
                    <SortToggle currentSort={sortKey} onChange={setSortKey} label="Created" sortKey="createTs" />
                  </th>
                  <th style={{ width: '20%' }}>
                    <SortToggle currentSort={sortKey} onChange={setSortKey} label="Updated" sortKey="updateTs" />
                  </th>
                  <th style={{ width: '25%' }}>
                    <SortToggle currentSort={sortKey} onChange={setSortKey} label="Owner" sortKey="email" />
                  </th>
                </tr>
              </thead>
              <tbody>
                {pageProjects.map((project) => {
                  const projectName = project.name.toLowerCase();
                  return (
                    <UISref key={projectName} to="home.project.dashboard" params={{ project: projectName }}>
                      <tr className="clickable">
                        <td>
                          <UISref to="home.project.dashboard" params={{ project: projectName }}>
                            <a>{projectName}</a>
                          </UISref>
                        </td>
                        <td>{timestamp(project.createTs)}</td>
                        <td>{timestamp(project.updateTs)}</td>
                        <td>{project.email}</td>
                      </tr>
                    </UISref>
                  );
                })}
              </tbody>
            </table>
            <PaginationControls onPageChanged={changePage} activePage={currentPage} totalPages={totalPages} />
          </>
        )}
      </div>
    </div>
  );
};
