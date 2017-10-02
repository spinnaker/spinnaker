import * as React from 'react';
import * as classNames from 'classnames';

interface IKayentaInputProps {
  className?: string;
}

export default function KayentaInput(props: IKayentaInputProps & React.ChangeTargetHTMLProps<HTMLInputElement>) {
  const { className, ...inputProps } = props;
  return (
    <input className={classNames('form-control', 'input-sm', className)} {...inputProps}/>
  );
}
