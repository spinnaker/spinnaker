plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  api("org.springframework.boot:spring-boot-starter-test")
  api("org.springframework.security:spring-security-test")
  api("org.springframework.security:spring-security-config")
  api("com.netflix.eureka:eureka-client")
  api("com.ninja-squad:springmockk:1.1.0")
}
