import { UISref } from '@uirouter/react';
import React from 'react';

import { Spinner } from '../../widgets/spinners/Spinner';

interface IDetailsProps {
  loading: boolean;
}

interface IDetailsHeaderProps {
  icon: React.ReactNode;
  name: string;
}

interface IDetailsSFCWithExtras extends React.SFC<IDetailsProps> {
  Header: React.SFC<IDetailsHeaderProps>;
}

const CloseButton = (
  <div className="close-button">
    <UISref to="^">
      <a className="btn btn-link">
        <span className="glyphicon glyphicon-remove" />
      </a>
    </UISref>
  </div>
);

const DetailsHeader: React.SFC<IDetailsHeaderProps> = (props) => (
  <div className="header">
    {CloseButton}
    <div className="header-text horizontal middle">
      {props.icon}
      <h3 className="horizontal middle space-between flex-1">{props.name}</h3>
    </div>
    <div>{props.children}</div>
  </div>
);

const loading = (
  <div className="header">
    <div className="horizontal center middle spinner-container">
      <Spinner />
    </div>
  </div>
);

const Details: IDetailsSFCWithExtras = (props) => (
  <div className="details-panel">{props.loading ? loading : props.children}</div>
);

Details.Header = DetailsHeader;

export { Details };
