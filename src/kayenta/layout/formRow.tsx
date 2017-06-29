import * as React from 'react';

export default function FormRow({ label, children }: { label: string, children?: any }) {
  return (
    <div className="form-group row">
      <label className="col-sm-3 control-label">
        {label}
      </label>
      <div className="col-sm-9">
        {children}
      </div>
    </div>
  );
}
