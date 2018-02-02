import * as React from 'react';
import { SelectCallback, Pagination } from 'react-bootstrap';

export interface IPaginationControlsProps {
  onPageChanged: SelectCallback;
  activePage: number;
  totalPages: number;
}

export const PaginationControls = ({ onPageChanged, activePage, totalPages }: IPaginationControlsProps) => {
  const ButtonComponent = (props: any) => {
    const { eventKey, children, ...restProps } = props;
    return <a {...restProps}>{children}</a>
  };

  return (
    <Pagination
      buttonComponentClass={ButtonComponent}
      className="clickable"
      ellipsis={true}
      next={true}
      prev={true}
      onSelect={onPageChanged}
      activePage={activePage}
      items={totalPages}
      maxButtons={10}
    />
  )
};
