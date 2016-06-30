package org.openstack4j.openstack.heat.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.openstack4j.model.heat.StackUpdate;
import org.openstack4j.model.heat.builder.StackUpdateBuilder;
import org.openstack4j.openstack.heat.utils.Environment;
import org.openstack4j.openstack.heat.utils.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO this is an updated version of the openstack4j source. The files method
 * was added to the builder to allow for stack updates. This change should eventually be
 * made into a PR against openstack4j.
 */

/**
 * Model Entity used for updating a Stack
 *
 * @author Jeremy Unruh
 */
public class HeatStackUpdate implements StackUpdate {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(HeatStackUpdate.class);

    @JsonProperty("template")
    private String template;
    @JsonProperty("template_url")
    private String templateURL;
    @JsonProperty("parameters")
    private Map<String, String> parameters;
    @JsonProperty("timeout_mins")
    private Long timeoutMins;
    @JsonProperty("environment")
    private String environment;
    @JsonProperty("files")
    private Map<String, String> files = new HashMap<String, String>();

    public static StackUpdateBuilder builder() {
        return new HeatStackUpdateConcreteBuilder();
    }

    @Override
    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public String getTemplate() {
        return template;
    }

    public String getTempateURL() {
        return templateURL;
    }

    public String getEnvironment(){
        return environment;
    }

    public Map<String, String> getFiles() {
        return files;
    }

    @Override
    public StackUpdateBuilder toBuilder() {
        return new HeatStackUpdateConcreteBuilder(this);
    }

    public static class HeatStackUpdateConcreteBuilder implements StackUpdateBuilder {

        private HeatStackUpdate model;

        public HeatStackUpdateConcreteBuilder() {
            this(new HeatStackUpdate());
        }

        public HeatStackUpdateConcreteBuilder(HeatStackUpdate model) {
            this.model = model;
        }

        @Override
        public StackUpdate build() {
            return model;
        }

        @Override
        public StackUpdateBuilder from(StackUpdate in) {
            model = (HeatStackUpdate) in;
            return this;
        }

        @Override
        public StackUpdateBuilder template(String template) {
            model.template = template;
            return this;
        }

        @Override
        public StackUpdateBuilder templateFromFile(String tplFile) {
            try {
                Template tpl = new Template(tplFile);
                model.template = tpl.getTplContent();
                model.files.putAll(tpl.getFiles());
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
            return this;
        }

        @Override
        public StackUpdateBuilder templateURL(String templateURL) {
            model.templateURL = templateURL;
            return this;
        }

        @Override
        public StackUpdateBuilder parameters(Map<String, String> parameters) {
            model.parameters = parameters;
            return this;
        }

        @Override
        public StackUpdateBuilder timeoutMins(Long timeoutMins) {
            model.timeoutMins = timeoutMins;
            return this;
        }

        @Override
        public StackUpdateBuilder environment(String environment){
            model.environment = environment;
            return this;
        }

        @Override
        public StackUpdateBuilder environmentFromFile(String envFile){
            try {
                Environment env = new Environment(envFile);
                model.environment = env.getEnvContent();
                model.files.putAll(env.getFiles());
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
            return this;
        }

        @Override
        public StackUpdateBuilder files(Map<String, String> files) {
            model.files = files;
            return this;
        }

    }
}

