package io.moderne.spinnaker.kork.atlas;

import io.micrometer.atlas.AtlasMeterRegistry;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

/**
 * Wires Micrometer common tags and an Atlas-specific {@code baseUnit} tag customizer for forked
 * Spinnaker JVM services.
 *
 * <p>Atlas URI / step / batch size flow through Spring Boot's built-in {@code
 * management.atlas.metrics.export.*} keys. This module ships with {@code enabled=false} as the
 * default; the deploy mechanism enables publishing by setting {@code MODERNE_ATLAS_URI} and {@code
 * management.atlas.metrics.export.enabled=true} together.
 */
@AutoConfiguration
@ConditionalOnClass(AtlasMeterRegistry.class)
@PropertySource("classpath:kork-atlas.properties")
public class AtlasMetricsAutoConfiguration {

  @Bean
  MeterFilter moderneCommonTags(
      @Value("${spring.application.name:unknown}") String applicationName) {
    return MeterFilter.commonTags(Ec2CommonTags.derive(applicationName));
  }

  @Bean
  MeterRegistryCustomizer<AtlasMeterRegistry> atlasBaseUnitTagCustomizer() {
    return registry -> registry.config().meterFilter(baseUnitMeterFilter());
  }

  static MeterFilter baseUnitMeterFilter() {
    return new MeterFilter() {
      @Override
      public Meter.Id map(Meter.Id id) {
        String unit = id.getBaseUnit();
        if (unit == null || unit.isEmpty()) {
          if (id.getType() == Meter.Type.TIMER || id.getType() == Meter.Type.LONG_TASK_TIMER) {
            unit = "seconds"; // matches AtlasMeterRegistry.baseTimeUnit()
          } else {
            return id;
          }
        }
        return id.withTag(Tag.of("baseUnit", unit));
      }
    };
  }
}
