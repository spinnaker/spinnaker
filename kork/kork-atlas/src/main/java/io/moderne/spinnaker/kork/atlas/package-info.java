/**
 * Atlas metrics autoconfiguration for forked Spinnaker JVM services. Provides a {@code MeterFilter}
 * that injects the Moderne common tag set (derived from EC2 IMDSv2 metadata + Frigga-parsed ASG
 * name, with a hostname-based dev fallback) and an Atlas-specific {@code MeterRegistryCustomizer}
 * that adds a {@code baseUnit} tag. Ships with {@code
 * management.atlas.metrics.export.enabled=false} by default — the deploy mechanism enables
 * publishing by setting that property and {@code MODERNE_ATLAS_URI} at the same time.
 *
 * <p>Phase 1 scope per <a
 * href="https://github.com/moderneinc/moderne-saas/issues/885">moderneinc/moderne-saas#885</a>.
 */
package io.moderne.spinnaker.kork.atlas;
