import * as Creators from 'kayenta/actions/creators';
import { KayentaAccountType } from 'kayenta/domain';
import { DISABLE_EDIT_CONFIG, DisableableSelect } from 'kayenta/layout/disableable';
import FormRow from 'kayenta/layout/formRow';
import { ICanaryState } from 'kayenta/reducers';
import { chain } from 'lodash';
import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

interface IMetricStoreSelectorStateProps {
  stores: string[];
  selectedStore: string;
}

interface IMetricStoreSelectorDispatchProps {
  select: (event: any) => void;
}

const MetricStoreSelector = ({
  stores,
  selectedStore,
  select,
}: IMetricStoreSelectorDispatchProps & IMetricStoreSelectorStateProps) => {
  if (stores.length < 2) {
    return null;
  }

  return (
    <FormRow label="Metric Store" inputOnly={true}>
      <DisableableSelect
        value={selectedStore || ''}
        onChange={select}
        className="form-control input-sm"
        disabledStateKeys={[DISABLE_EDIT_CONFIG]}
      >
        {stores.map((s) => (
          <option key={s} value={s}>
            {s}
          </option>
        ))}
      </DisableableSelect>
    </FormRow>
  );
};

const mapStateToProps = (state: ICanaryState): IMetricStoreSelectorStateProps => {
  return {
    stores: chain(state.data.kayentaAccounts.data)
      .filter((account) => account.supportedTypes.includes(KayentaAccountType.MetricsStore))
      .map((account) => account.metricsStoreType || account.type)
      .uniq()
      .sort()
      .valueOf(),
    selectedStore: state.selectedConfig.selectedStore,
  };
};

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>): IMetricStoreSelectorDispatchProps => {
  return {
    select: (event: any) => dispatch(Creators.selectMetricStore({ store: event.target.value })),
  };
};

export default connect(mapStateToProps, mapDispatchToProps)(MetricStoreSelector);
