package com.netflix.spinnaker.front50.model.delivery;

import com.netflix.spinnaker.front50.model.ItemDAO;
import java.util.Collection;

public interface DeliveryRepository extends ItemDAO<Delivery> {
  Collection<Delivery> getAllConfigs();

  Collection<Delivery> getConfigsByApplication(String application);

  Delivery upsertConfig(Delivery config);
}
