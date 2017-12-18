export interface ITableColumn<T> {
  label?: string;
  hide?: boolean;
  width: number;
  getContent: (data: T) => JSX.Element;
}
