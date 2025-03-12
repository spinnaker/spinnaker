import { useCurrentStateAndParams, useSrefActive } from '@uirouter/react';
import React from 'react';
import { useRecoilState } from 'recoil';

import { Icon } from '@spinnaker/presentation';
import { verticalNavExpandedAtom } from '../application/nav/navAtoms';
import { UserMenu } from '../authentication/userMenu/UserMenu';
import { CollapsibleSectionStateCache } from '../cache';
import { HelpMenu } from '../help/HelpMenu';
import { overridableComponent } from '../overrideRegistry';
import { GlobalSearch } from '../search/global/GlobalSearch';
import { logger } from '../utils';

import './SpinnakerHeader.css';

const LOG_CATEGORY = 'Navbar';

export const SpinnakerHeaderContent = () => {
  const { state: currentState } = useCurrentStateAndParams();
  const isApplicationView =
    currentState.name.includes('project.application.') || currentState.name.includes('applications.application.');

  const [verticalNavExpanded, setVerticalNavExpanded] = useRecoilState(verticalNavExpandedAtom);
  const toggleNav = () => {
    setVerticalNavExpanded(!verticalNavExpanded);
    CollapsibleSectionStateCache.setExpanded('verticalNav', !verticalNavExpanded);
    logger.log({ category: LOG_CATEGORY, action: !verticalNavExpanded ? 'ExpandVerticalNav' : 'CollapseVerticalNav' });
  };

  const isDevicePhoneOrSmaller = () => {
    const bodyStyles = window.getComputedStyle(document.body);
    const isPhone = bodyStyles.getPropertyValue('--is-phone');
    return isPhone.toLowerCase() === 'true';
  };
  const [navExpanded] = React.useState(!isDevicePhoneOrSmaller());

  const searchSref = useSrefActive('home.infrastructure', null, 'active');
  const projectsSref = useSrefActive('home.projects', null, 'active');
  const appsSref = useSrefActive('home.applications', null, 'active');
  const templatesSref = useSrefActive('home.pipeline-templates', null, 'active');

  const navItems = [
    {
      key: 'navHome',
      text: 'Search',
      srefProps: searchSref,
    },
    {
      key: 'navProjects',
      text: 'Projects',
      srefProps: projectsSref,
    },
    {
      key: 'navApplications',
      text: 'Applications',
      srefProps: appsSref,
    },
    {
      key: 'navPipelineTemplates',
      text: 'Pipeline Templates',
      srefProps: templatesSref,
    },
  ];

  return (
    <nav className="container spinnaker-header" role="navigation" aria-label="Main Menu">
      <div className="navbar-header horizontal middle">
        <div
          onClick={toggleNav}
          className={`nav-container navbar-menu-icon horizontal middle center sp-margin-xl-right ${
            isApplicationView ? 'app-view-menu' : ''
          }`}
        >
          {isApplicationView && (
            <Icon name={verticalNavExpanded ? 'menuClose' : 'menu'} size="medium" color="primary" />
          )}
        </div>
        <a className="navbar-brand flex-1" href="#">
          SPINNAKER
        </a>
      </div>
      {navExpanded && (
        <div className="nav-container nav-items">
          <ul className="nav nav-items flex-1 page-nav">
            {navItems.map((item) => {
              const { onClick, ...restProps } = item.srefProps;
              return (
                <li key={item.key}>
                  <a
                    {...restProps}
                    onClick={(e) => {
                      onClick(e);
                      logger.log({ category: LOG_CATEGORY, action: item.key });
                    }}
                  >
                    {item.text}
                  </a>
                </li>
              );
            })}
            <GlobalSearch />
          </ul>
          <ul className="nav nav-items">
            <UserMenu />
            <HelpMenu />
          </ul>
        </div>
      )}
    </nav>
  );
};

export const SpinnakerHeader = overridableComponent(SpinnakerHeaderContent, 'spinnakerHeader');
