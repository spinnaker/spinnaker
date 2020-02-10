package com.netflix.spinnaker.igor.config;

import static com.netflix.spinnaker.igor.config.JenkinsProperties.*;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Helper class to map masters in properties file into a validated property map */
@ConfigurationProperties(prefix = "jenkins")
@Validated
public class JenkinsProperties implements BuildServerProperties<JenkinsHost> {
  @Valid private List<JenkinsHost> masters;

  @Override
  public List<JenkinsHost> getMasters() {
    return masters;
  }

  public void setMasters(List<JenkinsHost> masters) {
    this.masters = masters;
  }

  public static class JenkinsHost implements BuildServerProperties.Host {
    @NotEmpty private String name;
    @NotEmpty private String address;
    private String username;
    private String password;
    private Boolean csrf = false;
    private String jsonPath;
    private List<String> oauthScopes = new ArrayList<String>();
    private String token;
    private Integer itemUpperThreshold;
    private String trustStore;
    private String trustStoreType = KeyStore.getDefaultType();
    private String trustStorePassword;
    private String keyStore;
    private String keyStoreType = KeyStore.getDefaultType();
    private String keyStorePassword;
    private Boolean skipHostnameVerification = false;
    private Permissions.Builder permissions = new Permissions.Builder();

    @Override
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    @Override
    public String getAddress() {
      return address;
    }

    public void setAddress(String address) {
      this.address = address;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public Boolean getCsrf() {
      return csrf;
    }

    public void setCsrf(Boolean csrf) {
      this.csrf = csrf;
    }

    public String getJsonPath() {
      return jsonPath;
    }

    public void setJsonPath(String jsonPath) {
      this.jsonPath = jsonPath;
    }

    public List<String> getOauthScopes() {
      return oauthScopes;
    }

    public void setOauthScopes(List<String> oauthScopes) {
      this.oauthScopes = oauthScopes;
    }

    public String getToken() {
      return token;
    }

    public void setToken(String token) {
      this.token = token;
    }

    public Integer getItemUpperThreshold() {
      return itemUpperThreshold;
    }

    public void setItemUpperThreshold(Integer itemUpperThreshold) {
      this.itemUpperThreshold = itemUpperThreshold;
    }

    public String getTrustStore() {
      return trustStore;
    }

    public void setTrustStore(String trustStore) {
      this.trustStore = trustStore;
    }

    public String getTrustStoreType() {
      return trustStoreType;
    }

    public void setTrustStoreType(String trustStoreType) {
      this.trustStoreType = trustStoreType;
    }

    public String getTrustStorePassword() {
      return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
      this.trustStorePassword = trustStorePassword;
    }

    public String getKeyStore() {
      return keyStore;
    }

    public void setKeyStore(String keyStore) {
      this.keyStore = keyStore;
    }

    public String getKeyStoreType() {
      return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
      this.keyStoreType = keyStoreType;
    }

    public String getKeyStorePassword() {
      return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
      this.keyStorePassword = keyStorePassword;
    }

    public Boolean getSkipHostnameVerification() {
      return skipHostnameVerification;
    }

    public void setSkipHostnameVerification(Boolean skipHostnameVerification) {
      this.skipHostnameVerification = skipHostnameVerification;
    }

    @Override
    public Permissions.Builder getPermissions() {
      return permissions;
    }

    public void setPermissions(Permissions.Builder permissions) {
      this.permissions = permissions;
    }
  }
}
