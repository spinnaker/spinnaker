import * as React from 'react';

export interface IDeleteButtonProps {
  onClick: () => void;
}

export default ({ onClick }: IDeleteButtonProps) => (
  <a className="clickable" onClick={onClick}>
    <span className="glyphicon glyphicon-trash"/>
  </a>
);
