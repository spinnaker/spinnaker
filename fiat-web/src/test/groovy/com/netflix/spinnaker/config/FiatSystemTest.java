package com.netflix.spinnaker.config;

import com.netflix.spinnaker.fiat.Main;
import com.netflix.spinnaker.fiat.config.RedisConfig;
import com.netflix.spinnaker.fiat.config.ResourcesConfig;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// This file must be .java because groovy barfs on the composite annotation style.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebAppConfiguration()
@TestPropertySource("/fiat.properties")
@DirtiesContext
@ContextConfiguration(classes = {
    RedisConfig.class,
    TestUserRoleProviderConfig.class,
    ResourcesConfig.class,
    Main.class}
)
public @interface FiatSystemTest {
}
