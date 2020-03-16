import React from 'react';

import { parseName } from './Frigga';

import './ArtifactDetailHeader.less';

export interface IArtifactDetailHeaderProps {
  name: string;
  version: string;
  onRequestClose: () => any;
}

export const ArtifactDetailHeader = ({ name, version, onRequestClose }: IArtifactDetailHeaderProps) => {
  const { version: packageVersion, buildNumber } = parseName(version);

  return (
    <div className="ArtifactDetailHeader flex-container-h space-between middle text-bold">
      <div className="header-section-left flex-container-h middle">
        {/* embed SVG direclty here until we get either a new SVG impl
            for icons (in the works) or move these into the icon font */}
        <svg
          version="1.1"
          xmlns="http://www.w3.org/2000/svg"
          xmlnsXlink="http://www.w3.org/1999/xlink"
          x="0px"
          y="0px"
          viewBox="0 0 24 24"
          xmlSpace="preserve"
          fill="white"
          width="40px"
        >
          <g>
            <path
              d="M1.5,24C0.673,24,0,23.327,0,22.5V7.82c0-0.165,0.029-0.331,0.085-0.494C0.088,7.318,0.095,7.3,0.099,7.293
		c0.04-0.115,0.104-0.237,0.18-0.345l4.514-6.32C5.074,0.235,5.53,0,6.014,0h11.971c0.485,0,0.941,0.235,1.222,0.628l4.514,6.32
		c0.077,0.107,0.14,0.23,0.189,0.365C23.971,7.488,24,7.655,24,7.82V22.5c0,0.827-0.673,1.5-1.5,1.5H1.5z M1,22.5
		C1,22.776,1.224,23,1.5,23h21c0.276,0,0.5-0.224,0.5-0.5V8H1V22.5z M22.529,7l-4.135-5.791C18.3,1.078,18.147,1,17.986,1H12.5v6
		H22.529z M11.5,7V1H6.014C5.853,1,5.701,1.078,5.607,1.209L1.471,7H11.5z"
            />
            <path
              d="M13.001,21c-0.552,0-1-0.448-1-1v-2c0-0.552,0.448-1,1-1h7c0.552,0,1,0.448,1,1v2c0,0.552-0.448,1-1,1H13.001z M13.001,20
		h7v-2h-7L13.001,20z"
            />
          </g>
        </svg>
        <span className="header-version-pill">{buildNumber ? `#${buildNumber}` : packageVersion || version}</span>
      </div>

      <div className="header-section-center">{name}</div>

      <div className="header-close flex-container-h center middle" onClick={() => onRequestClose()}>
        {/* embed SVG direclty here until we get either a new SVG impl
            for icons (in the works) or move these into the icon font */}
        <svg
          version="1.1"
          xmlns="http://www.w3.org/2000/svg"
          xmlnsXlink="http://www.w3.org/1999/xlink"
          x="0px"
          y="0px"
          viewBox="0 0 24 24"
          xmlSpace="preserve"
          fill="white"
          width="24px"
        >
          <g>
            <path
              d="M23.5,23.999c-0.134,0-0.259-0.052-0.354-0.146L12,12.706L0.854,23.853c-0.094,0.094-0.22,0.146-0.354,0.146
		s-0.259-0.052-0.354-0.146c-0.195-0.195-0.195-0.512,0-0.707l11.146-11.146L0.147,0.853c-0.195-0.195-0.195-0.512,0-0.707
		C0.241,0.051,0.367-0.001,0.5-0.001s0.259,0.052,0.354,0.146L12,11.292L23.147,0.146c0.094-0.094,0.22-0.146,0.354-0.146
		s0.259,0.052,0.354,0.146c0.195,0.195,0.195,0.512,0,0.707L12.707,11.999l11.146,11.146c0.195,0.195,0.195,0.512,0,0.707
		C23.759,23.947,23.634,23.999,23.5,23.999z"
            />
          </g>
        </svg>
      </div>
    </div>
  );
};
