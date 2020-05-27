package com.netflix.spinnaker.front50.events;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UnmodifiableAttributesApplicationEventListener implements ApplicationEventListener {

  @Override
  public boolean supports(Type type) {
    return type.equals(Type.PRE_UPDATE);
  }

  @Override
  public void accept(ApplicationModelEvent applicationModelEvent) {
    Optional.ofNullable(applicationModelEvent.original)
        .ifPresent(
            original -> {
              applicationModelEvent.updated.setName(original.getName());
              applicationModelEvent.updated.setUpdateTs(original.getUpdateTs());
              applicationModelEvent.updated.setCreateTs(original.getCreateTs());
            });
  }
}
