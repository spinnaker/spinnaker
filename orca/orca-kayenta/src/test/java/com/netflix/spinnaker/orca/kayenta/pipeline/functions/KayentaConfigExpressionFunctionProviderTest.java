package com.netflix.spinnaker.orca.kayenta.pipeline.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.netflix.spinnaker.kork.expressions.SpelHelperFunctionException;
import com.netflix.spinnaker.orca.kayenta.KayentaCanaryConfig;
import com.netflix.spinnaker.orca.kayenta.KayentaService;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class KayentaConfigExpressionFunctionProviderTest {

  @Test
  public void missingName() {
    KayentaConfigExpressionFunctionProvider provider =
        new KayentaConfigExpressionFunctionProvider(Mockito.mock(KayentaService.class));
    assertThrows(
        SpelHelperFunctionException.class, () -> provider.canaryConfigNameToId(null, "myapp"));
  }

  @Test
  public void missingApp() {
    KayentaConfigExpressionFunctionProvider provider =
        new KayentaConfigExpressionFunctionProvider(Mockito.mock(KayentaService.class));
    assertThrows(
        SpelHelperFunctionException.class, () -> provider.canaryConfigNameToId("myname", null));
  }

  @Test
  public void conversionWorks() {
    KayentaService kayentaService = Mockito.mock(KayentaService.class);
    List<KayentaCanaryConfig> canaryConfigs = Lists.newArrayList();
    KayentaCanaryConfig config =
        new KayentaCanaryConfig("myconfig", "myname", 0L, null, Collections.singletonList("myapp"));
    canaryConfigs.add(config);
    when(kayentaService.getAllCanaryConfigs()).thenReturn(canaryConfigs);

    KayentaConfigExpressionFunctionProvider provider =
        new KayentaConfigExpressionFunctionProvider(kayentaService);
    String configId = provider.canaryConfigNameToId("myname", "myapp");

    assertEquals("myconfig", configId);
  }

  @Test
  public void nothingFound() {
    KayentaService kayentaService = Mockito.mock(KayentaService.class);
    List<KayentaCanaryConfig> canaryConfigs = Lists.newArrayList();
    KayentaCanaryConfig config =
        new KayentaCanaryConfig("myconfig", "myname", 0L, null, Collections.singletonList("myapp"));
    canaryConfigs.add(config);
    when(kayentaService.getAllCanaryConfigs()).thenReturn(canaryConfigs);

    KayentaConfigExpressionFunctionProvider provider =
        new KayentaConfigExpressionFunctionProvider(kayentaService);
    assertThrows(
        SpelHelperFunctionException.class,
        () -> provider.canaryConfigNameToId("someothername", "myapp"));
  }
}
