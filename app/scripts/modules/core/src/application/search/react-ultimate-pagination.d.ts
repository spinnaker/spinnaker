declare module 'react-ultimate-pagination' {
  export interface ItemTypeToComponent {
    PAGE: React.ComponentType<{ value: any; isActive: boolean; onClick: any }>;
    ELLIPSIS: React.ComponentType<{ value: any; isActive: boolean; onClick: any }>;
    FIRST_PAGE_LINK: React.ComponentType<{ value: any; isActive: boolean; onClick: any }>;
    PREVIOUS_PAGE_LINK: React.ComponentType<{ value: any; isActive: boolean; onClick: any }>;
    NEXT_PAGE_LINK: React.ComponentType<{ value: any; isActive: boolean; onClick: any }>;
    LAST_PAGE_LINK: React.ComponentType<{ value: any; isActive: boolean; onClick: any }>;
  }

  export interface IUltimatePaginationProps {
    currentPage: number;
    totalPages: number;
    boundaryPagesRange?: number;
    siblingPagesRange?: number;
    hideEllipsis?: boolean;
    hidePreviousAndNextPageLinks?: boolean;
    hideFirstAndLastPageLinks?: boolean;
    onChange?: Function;
    disabled?: boolean;
  }

  export function createUltimatePagination(options: {
    itemTypeToComponent: ItemTypeToComponent;
    WrapperComponent: React.ComponentClass = 'div';
  }): React.ComponentClass<IUltimatePaginationProps>;
  export interface ItemTypes {
    PAGE: 'PAGE';
    ELLIPSIS: 'ELLIPSIS';
    FIRST_PAGE_LINK: 'FIRST_PAGE_LINK';
    PREVIOUS_PAGE_LINK: 'PREVIOUS_PAGE_LINK';
    NEXT_PAGE_LINK: 'NEXT_PAGE_LINK';
    LAST_PAGE_LINK: 'LAST_PAGE_LINK';
  }

  export declare const ITEM_TYPES: ItemTypes;
}
