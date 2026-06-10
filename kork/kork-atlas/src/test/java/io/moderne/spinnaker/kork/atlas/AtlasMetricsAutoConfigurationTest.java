package io.moderne.spinnaker.kork.atlas;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.atlas.AtlasMeterRegistry;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AtlasMetricsAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AtlasMetricsAutoConfiguration.class))
          .withPropertyValues("spring.application.name=clouddriver");

  @Test
  void wiresCommonTagsFilterWhenAtlasRegistryOnClasspath() {
    contextRunner.run(
        context -> {
          assertThat(context).hasBean("moderneCommonTags");
          assertThat(context.getBean("moderneCommonTags")).isInstanceOf(MeterFilter.class);
        });
  }

  @Test
  void wiresBaseUnitCustomizerWhenAtlasRegistryOnClasspath() {
    contextRunner.run(
        context -> {
          assertThat(context).hasBean("atlasBaseUnitTagCustomizer");
          assertThat(context.getBean("atlasBaseUnitTagCustomizer"))
              .isInstanceOf(MeterRegistryCustomizer.class);
        });
  }

  @Test
  void neitherBeanIsWiredWhenAtlasRegistryIsAbsent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(AtlasMeterRegistry.class))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean("moderneCommonTags");
              assertThat(context).doesNotHaveBean("atlasBaseUnitTagCustomizer");
            });
  }

  @Test
  void baseUnitMeterFilter_addsSecondsToTimerWithoutExplicitUnit() {
    Meter.Id id =
        new Meter.Id("http.server.requests", Tags.empty(), null, "desc", Meter.Type.TIMER);
    Meter.Id mapped = AtlasMetricsAutoConfiguration.baseUnitMeterFilter().map(id);

    assertThat(mapped.getTag("baseUnit")).isEqualTo("seconds");
  }

  @Test
  void baseUnitMeterFilter_addsSecondsToLongTaskTimerWithoutExplicitUnit() {
    Meter.Id id = new Meter.Id("foo.long", Tags.empty(), null, "desc", Meter.Type.LONG_TASK_TIMER);
    Meter.Id mapped = AtlasMetricsAutoConfiguration.baseUnitMeterFilter().map(id);

    assertThat(mapped.getTag("baseUnit")).isEqualTo("seconds");
  }

  @Test
  void baseUnitMeterFilter_preservesExplicitBaseUnit() {
    Meter.Id id = new Meter.Id("foo.bytes", Tags.empty(), "bytes", "desc", Meter.Type.GAUGE);
    Meter.Id mapped = AtlasMetricsAutoConfiguration.baseUnitMeterFilter().map(id);

    assertThat(mapped.getTag("baseUnit")).isEqualTo("bytes");
  }

  @Test
  void baseUnitMeterFilter_doesNotAddTagToCounterWithoutBaseUnit() {
    Meter.Id id = new Meter.Id("foo.events", Tags.empty(), null, "desc", Meter.Type.COUNTER);
    Meter.Id mapped = AtlasMetricsAutoConfiguration.baseUnitMeterFilter().map(id);

    assertThat(mapped.getTag("baseUnit")).isNull();
  }
}
