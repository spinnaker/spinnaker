import { INumberListConstraints } from './numberList.component';

export interface INumberListProps {
  model: number[] | string;
  constraints?: INumberListConstraints;
  label: string;
  onChange: (model: number[] | string) => void;
}
