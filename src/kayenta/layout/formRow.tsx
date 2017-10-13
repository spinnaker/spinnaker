import * as React from 'react';

export default function FormRow({ label, children }: { label?: string, children?: any }) {
  return (
    <div className="form-group row">
      <label className="col-sm-2 control-label">
        {label}
      </label>
      <div className="col-sm-10">
        {children}
      </div>
    </div>
  );
}
