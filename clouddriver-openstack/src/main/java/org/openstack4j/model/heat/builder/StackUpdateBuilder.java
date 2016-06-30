package org.openstack4j.model.heat.builder;

import java.util.Map;

import org.openstack4j.common.Buildable;
import org.openstack4j.model.heat.StackUpdate;

/**
 * TODO this is an updated version of the openstack4j source. The files method
 * was added to the builder to allow for stack updates. This change should eventually be
 * made into a PR against openstack4j.
 */

/**
 * A builder which creates a StackUpdate entity
 *
 * @author Jeremy Unruh
 */
public interface StackUpdateBuilder extends Buildable.Builder<StackUpdateBuilder, StackUpdate> {

    /**
     * Sets the template in YAML/JSON format.  If the template begins with a "{" then JSON is assumed
     * @param template the template
     * @return StackUpdateBuilder
     */
    StackUpdateBuilder template(String template);

    /**
     * Sets the template URL
     * @param templateURL the template URL
     * @return StackUpdateBuilder
     */
    StackUpdateBuilder templateURL(String templateURL);

    /**
     * Sets the parameters which are passed to the server. It might contain Information about flavor, image, etc.
     * @param parameters Map of parameters. Key is name, value is the value of the parameters
     * @return the modified StackUpdateBuilder
     */
    StackUpdateBuilder parameters(Map<String,String> parameters);

    /**
     * Sets the stack creation timeout in minutes
     * @param timeoutMins timeout in minutes
     * @return the modified StackUpdateBuilder
     */
    StackUpdateBuilder timeoutMins(Long timeoutMins);

    StackUpdateBuilder environment(String environment);

    StackUpdateBuilder environmentFromFile(String envFile);

    StackUpdateBuilder templateFromFile(String tplFile);

    StackUpdateBuilder files(Map<String, String> files);

}
