package com.netflix.spinnaker.front50.model.delivery;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.config.StorageServiceConfigurationProperties;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectType;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.StorageServiceSupport;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.reactivex.rxjava3.core.Scheduler;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.util.Assert;

public class DefaultDeliveryRepository extends StorageServiceSupport<Delivery>
    implements DeliveryRepository {
  public DefaultDeliveryRepository(
      StorageService service,
      Scheduler scheduler,
      ObjectKeyLoader objectKeyLoader,
      StorageServiceConfigurationProperties.PerObjectType configurationProperties,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    super(
        ObjectType.DELIVERY,
        service,
        scheduler,
        objectKeyLoader,
        configurationProperties,
        registry,
        circuitBreakerRegistry);
  }

  @Override
  public Collection<Delivery> getAllConfigs() {
    return all();
  }

  @Override
  public Collection<Delivery> getConfigsByApplication(String application) {
    return getConfigsByApplication(application, true);
  }

  private Collection<Delivery> getConfigsByApplication(String application, boolean refresh) {
    return all(refresh).stream()
        .filter(
            config ->
                config.getApplication() != null
                    && config.getApplication().equalsIgnoreCase(application))
        .collect(Collectors.toList());
  }

  @Override
  public Delivery upsertConfig(Delivery config) {
    return create(config.getId(), config);
  }

  @Override
  public Delivery create(String id, Delivery deliveryConfig) {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
    deliveryConfig.setId(id);

    if (deliveryConfig.getCreateTs() == null) {
      deliveryConfig.setCreateTs(System.currentTimeMillis());
    } else {
      deliveryConfig.setLastModified(System.currentTimeMillis());
    }

    Assert.notNull(deliveryConfig.getApplication(), "application field must NOT be null!");
    // todo eb: what other validation needs to happen here?

    update(id, deliveryConfig);
    return findById(id);
  }
}
