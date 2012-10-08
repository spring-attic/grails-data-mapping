package grails.gorm.tests

import org.grails.datastore.mapping.core.Session
import grails.gorm.DetachedCriteria

/**
 * @author graemerocher
 */
class DomainEventsSpec extends GormDatastoreSpec {

    def setup() {
        PersonEvent.resetStore()
    }

    void "Test modify property before save"() {
        given:
            session.datastore.mappingContext.addPersistentEntity(ModifyPerson)
            def p = new ModifyPerson(name:"Bob").save(flush:true)
            session.clear()

        when:"An object is queried by id"
            p = ModifyPerson.get(p.id)

        then: "the correct object is returned"
            p.name == "Fred"

        when:"An object is queried by the updated value"
            p = ModifyPerson.findByName("Fred")

        then:"The correct person is returned"
            p.name == "Fred"
    }

    void "Test auto time stamping working"() {

        given:
            def p = new PersonEvent()

            p.name = "Fred"
            p.save(flush:true)
            session.clear()

        when:
            p = PersonEvent.get(p.id)

        then:
            sleep(2000)

            p.dateCreated == p.lastUpdated

        when:
            p.name = "Wilma"
            p.save(flush:true)

        then:
            p.dateCreated.before(p.lastUpdated)
    }

    void "Test delete events"() {
        given:
            def p = new PersonEvent()
            p.name = "Fred"
            p.save(flush:true)
            session.clear()

        when:
            p = PersonEvent.get(p.id)

        then:
            0 == PersonEvent.STORE.beforeDelete
            0 == PersonEvent.STORE.afterDelete

        when:
            p.delete(flush:true)

        then:
            1 == PersonEvent.STORE.beforeDelete
            1 == PersonEvent.STORE.afterDelete
    }

    void "Test multi-delete events"() {
        given:
            def freds = (1..3).collect {
                new PersonEvent(name: "Fred$it").save(flush:true)
            }
            session.clear()

        when:
            freds = PersonEvent.findAllByIdInList(freds*.id)

        then:
            3 == freds.size()
            0 == PersonEvent.STORE.beforeDelete
            0 == PersonEvent.STORE.afterDelete

        when:
            new DetachedCriteria(PersonEvent).build {
                'in'('id', freds*.id)
            }.deleteAll()
            session.flush()
            freds = PersonEvent.findAllByIdInList(freds*.id)

        then:
            0 == freds.size()
            3 == PersonEvent.STORE.beforeDelete
            3 == PersonEvent.STORE.afterDelete
    }

    void "Test before update event"() {
        given:
            def p = new PersonEvent()

            p.name = "Fred"
            p.save(flush:true)
            session.clear()

        when:
            p = PersonEvent.get(p.id)

        then:
            "Fred" == p.name
            0 == PersonEvent.STORE.beforeUpdate
            0 == PersonEvent.STORE.afterUpdate

        when:
            p.name = "Bob"
            p.save(flush:true)
            session.clear()
            p = PersonEvent.get(p.id)

        then:
            "Bob" == p.name
            1 == PersonEvent.STORE.beforeUpdate
            1 == PersonEvent.STORE.afterUpdate
    }

    void "Test insert events"() {
        given:
            def p = new PersonEvent()

            p.name = "Fred"
            p.save(flush:true)
            session.clear()

        when:
            p = PersonEvent.get(p.id)

        then:
            "Fred" == p.name
            0 == PersonEvent.STORE.beforeUpdate
            1 == PersonEvent.STORE.beforeInsert
            0 == PersonEvent.STORE.afterUpdate
            1 == PersonEvent.STORE.afterInsert

        when:
            p.name = "Bob"
            p.save(flush:true)
            session.clear()
            p = PersonEvent.get(p.id)

        then:
            "Bob" == p.name
            1 == PersonEvent.STORE.beforeUpdate
            1 == PersonEvent.STORE.beforeInsert
            1 == PersonEvent.STORE.afterUpdate
            1 == PersonEvent.STORE.afterInsert
    }

    void "Test load events"() {
        given:
            def p = new PersonEvent()

            p.name = "Fred"
            p.save(flush:true)
            session.clear()

        when:
            p = PersonEvent.get(p.id)

        then:
            "Fred" == p.name
            if (!'JpaSession'.equals(session.getClass().simpleName)) {
                // JPA doesn't seem to support a pre-load event
                1 == PersonEvent.STORE.beforeLoad
            }
            1 == PersonEvent.STORE.afterLoad
    }

    void "Test multi-load events"() {
        given:
            def freds = (1..3).collect {
                new PersonEvent(name: "Fred$it").save(flush:true)
            }
            session.clear()

        when:
            freds = PersonEvent.findAllByIdInList(freds*.id)

        then:
            3 == freds.size()
            if (!'JpaSession'.equals(session.getClass().simpleName)) {
                // JPA doesn't seem to support a pre-load event
                3 == PersonEvent.STORE.beforeLoad
            }
            3 == PersonEvent.STORE.afterLoad
    }

    void "Test bean autowiring"() {
        given:
            def personService = new Object()
            session.datastore.applicationContext.beanFactory.registerSingleton 'personService', personService

            def p = new PersonEvent()
            p.name = "Fred"
            p.save(flush:true)
            session.clear()

        when:
            p = PersonEvent.get(p.id)

        then:
            "Fred" == p.name
            personService.is p.personService
    }

    def cleanup() {
        session.datastore.applicationContext.beanFactory.destroySingleton 'personService'
    }
}

class PersonEvent implements Serializable {
    Long id
    Long version
    String name
    Date dateCreated
    Date lastUpdated

    def personService

    static STORE_INITIAL = [
        beforeDelete: 0, afterDelete: 0,
        beforeUpdate: 0, afterUpdate: 0,
        beforeInsert: 0, afterInsert: 0,
        beforeLoad: 0, afterLoad: 0]

    static STORE = [:] + STORE_INITIAL

    static void resetStore() {
        STORE = [:] + STORE_INITIAL
    }

    def beforeDelete() {
        STORE.beforeDelete++
    }

    void afterDelete() {
        STORE.afterDelete++
    }

    def beforeUpdate() {
        STORE.beforeUpdate++
    }

    void afterUpdate() {
        STORE.afterUpdate++
    }

    def beforeInsert() {
        STORE.beforeInsert++
    }

    void afterInsert() {
        STORE.afterInsert++
    }

    void beforeLoad() {
        STORE.beforeLoad++
    }

    void afterLoad() {
        STORE.afterLoad++
    }
}

class ModifyPerson implements Serializable {
    Long id
    Long version

    String name

    static mapping = {
        name index:true
    }

    def beforeInsert() {
        name = "Fred"
    }
}
