package com.netflix.spinnaker.kato.config

import com.netflix.spinnaker.kato.gce.model.GoogleInstanceTypePersistentDisk
import com.netflix.spinnaker.kato.gce.model.GooglePersistentDisk
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty('google.enabled')
@Configuration
@ComponentScan('com.netflix.spinnaker.kato.gce')
class GceConfig {
  private static final String DEFAULT_KEY = "default"
  private static final String DISK_TYPE = "pd-standard"
  private static final long DISK_SIZE_GB = 100

  @Bean
  @ConfigurationProperties('google.defaults')
  DeployDefaults gceDeployDefaults() {
    new DeployDefaults()
  }

  static class DeployDefaults {
    List<GoogleInstanceTypePersistentDisk> instanceTypePersistentDisks = []

    GooglePersistentDisk determinePersistentDisk(String instanceType) {
      def instanceTypePersistentDisk = instanceTypePersistentDisks.find {
        it.instanceType == instanceType
      }

      if (!instanceTypePersistentDisk) {
        instanceTypePersistentDisk = instanceTypePersistentDisks.find {
          it.instanceType == DEFAULT_KEY
        }
      }

      if (!instanceTypePersistentDisk) {
        instanceTypePersistentDisk =
                new GoogleInstanceTypePersistentDisk(instanceType: DEFAULT_KEY,
                                                     persistentDisk: new GooglePersistentDisk(type: DISK_TYPE,
                                                                                              size: DISK_SIZE_GB))
      }

      return instanceTypePersistentDisk.persistentDisk
    }
  }
}
