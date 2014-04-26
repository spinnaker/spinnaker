package com.netflix.front50

import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.model.Attribute
import com.amazonaws.services.simpledb.model.Item
import com.amazonaws.services.simpledb.model.ReplaceableAttribute
import com.amazonaws.services.simpledb.model.SelectResult
import spock.lang.Specification

/**
 * Created by aglover on 4/22/14.
 */
class SimpleDBDAOTest extends Specification {

    void 'should update an existing record'() {
        def awsClient = Mock(AmazonSimpleDB)
        def attributes = [
                "group": "tst-group",
                "tags" : "[1,ok, test]"]
        def dao = new SimpleDBDAO()
        dao.awsClient = awsClient
        when:
        dao.update("SampleApp1", attributes)
        then:
        1 * awsClient.putAttributes(_)
    }

    void 'should transform a properties map into another one'() {
        def attributes = [
                "group"      : "tst-group",
                "type"       : "test type",
                "description": "test",
                "owner"      : "Kevin McEntee",
                "email"      : "web@netflix.com",
                "updateTs"   : "1265752693581",
                "tags"       : "[1,ok, test]"]
        def dao = new SimpleDBDAO()
        def values = dao.buildAttributes(attributes, false)
        def attr = values.find { it.name == "description" }
        expect:
        values != null
        values.size() == 7
        values[0].class == ReplaceableAttribute.class
        attr.value == "test"
    }

    void 'should save'() {
        def awsClient = Mock(AmazonSimpleDB)

        def attributes = [
                "group"      : "tst-group",
                "type"       : "test type",
                "description": "test",
                "owner"      : "Kevin McEntee",
                "email"      : "web@netflix.com",
                "updateTs"   : "1265752693581",
                "tags"       : "[1,ok, test]"]
        def dao = new SimpleDBDAO()
        dao.awsClient = awsClient
        when:
        def application = dao.create("SampleApp1", attributes)
        then:
        application.email == 'web@netflix.com'
        application.createTs != null
        1 * awsClient.putAttributes(_)
    }

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
