package com.netflix.front50

import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.model.Attribute
import com.amazonaws.services.simpledb.model.Item
import com.amazonaws.services.simpledb.model.SelectResult
import spock.lang.Specification

/**
 * Created by aglover on 4/22/14.
 */
class SimpleDBDAOTest extends Specification {

    void 'should throw exception if no application is found'() {
        def awsClient = Mock(AmazonSimpleDB)
        def result = Mock(SelectResult)
        def dao = new SimpleDBDAO()
        List<Item> outItems = new ArrayList<Item>() //nothing was found
        result.getItems() >> outItems
        awsClient.select(_) >> result
        dao.awsClient = awsClient
        when:
        dao.findByName("SAMPLEAPP")

        then:
        final Exception exp = thrown()
        exp.message == "No Application found by name of SAMPLEAPP in domain RESOURCE_REGISTRY"
    }

    void 'should throw exception if no applications exist'() {
        def awsClient = Mock(AmazonSimpleDB)
        def result = Mock(SelectResult)
        def dao = new SimpleDBDAO()
        List<Item> outItems = new ArrayList<Item>() //nothing was found
        result.getItems() >> outItems
        awsClient.select(_) >> result
        dao.awsClient = awsClient
        when:
        dao.all()

        then:
        final Exception exp = thrown()
        exp.message == "No Applications found in domain RESOURCE_REGISTRY"
    }

    void 'should find one application by name'() {
        def awsClient = Mock(AmazonSimpleDB)
        def result = Mock(SelectResult)
        def dao = new SimpleDBDAO()
        List<Item> outItems = new ArrayList<Item>()
        Item item = new Item().withName("SAMPLEAPP").withAttributes(
                new Attribute("email", "web@netflix.com"), new Attribute("createTs", "1265752693581"),
                new Attribute("updateTs", "1265752693581"), new Attribute("description", "netflix.com application"),
                new Attribute("owner", "Kevin McEntee"), new Attribute("type", "Standalone Application"))
        outItems << item
        result.getItems() >> outItems
        awsClient.select(_) >> result

        dao.awsClient = awsClient

        def app = dao.findByName("SAMPLEAPP")

        expect:
        app != null
        app.name == "SAMPLEAPP"
        app.description == "netflix.com application"
        app.owner == "Kevin McEntee"
        app.type == "Standalone Application"
    }

    void 'should find all applications'() {
        def awsClient = Mock(AmazonSimpleDB)
        def result = Mock(SelectResult)
        def dao = new SimpleDBDAO()

        List<Item> outItems = new ArrayList<Item>()
        outItems << new Item().withName("SAMPLEAPP").withAttributes(
                new Attribute("email", "web@netflix.com"), new Attribute("createTs", "1265752693581"),
                new Attribute("updateTs", "1265752693581"), new Attribute("description", "netflix.com application"),
                new Attribute("owner", "Kevin McEntee"), new Attribute("type", "Standalone Application"))
        outItems << new Item().withName("SAMPLEAPP_2").withAttributes(
                new Attribute("email", "web@netflix.com"), new Attribute("createTs", "1265752693581"),
                new Attribute("updateTs", "1265752693581"), new Attribute("description", "netflix.com application"),
                new Attribute("owner", "Kevin McEntee"), new Attribute("type", "Standalone Application"))

        result.getItems() >> outItems
        awsClient.select(_) >> result

        dao.awsClient = awsClient

        def apps = dao.all()

        expect:
        apps != null
        apps.size() == 2
    }
}
