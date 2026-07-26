import React from 'react';

import './regionFilter/regionFilter.component.less';

export interface IRegionFilterProps {
  onClear: () => void;
  onToggleRegion: (region: string) => void;
  regions: string[];
  selectedRegions: { [region: string]: boolean };
}

export const RegionFilter = ({ onClear, onToggleRegion, regions, selectedRegions }: IRegionFilterProps) => {
  const [isOpen, setIsOpen] = React.useState(false);
  const rootRef = React.useRef<HTMLElement>(null);

  React.useEffect(() => {
    if (!isOpen) {
      return undefined;
    }

    const closeOnOutsideClick = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    document.addEventListener('click', closeOnOutsideClick);

    return () => document.removeEventListener('click', closeOnOutsideClick);
  }, [isOpen]);

  const clear = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    onClear();
  };

  return (
    <region-filter>
      <span ref={rootRef} className={`dropdown pull-right clickable ${isOpen ? 'open' : ''}`}>
        <h6 className="dropdown-toggle clickable" onClick={() => setIsOpen(!isOpen)}>
          <span className="region-filter-button">Filter by region / namespace</span>
          <span className="small glyphicon glyphicon-chevron-down" />
        </h6>
        {isOpen && (
          <ul className="dropdown-menu" role="menu">
            {regions.map((region) => (
              <li key={region} onClick={() => onToggleRegion(region)}>
                <a>
                  <label>{region}</label>
                  <input className="pull-right" type="checkbox" checked={!!selectedRegions[region]} readOnly />
                </a>
              </li>
            ))}
            <li className="text-center">
              <a href="#" onClick={clear}>
                Clear all
              </a>
            </li>
          </ul>
        )}
      </span>
    </region-filter>
  );
};
