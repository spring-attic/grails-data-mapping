package org.grails.datastore.mapping.mongo

import org.junit.Test

class LongIdTests extends AbstractMongoTest {

    @Test
    void testBasicPersistenceOperations() {
        md.mappingContext.addPersistentEntity(MongoLongIdEntity)

        MongoSession session = md.connect()

        session.nativeInterface.dropDatabase()

        def te = new MongoLongIdEntity(name:"Bob")

        session.persist te
        session.flush()

        assert te != null
        assert te.id != null
        assert te.id instanceof Long
		assert te.id == 1
		
		long previousId = te.id
		
        session.clear()
        te = session.retrieve(MongoLongIdEntity, te.id)

        assert te != null
        assert te.name == "Bob"

        te.name = "Fred"
        session.persist(te)
        session.flush()
        session.clear()

        te = session.retrieve(MongoLongIdEntity, te.id)
        assert te != null
        assert te.id != null
        assert te.name == 'Fred'

        session.delete te
        session.flush()

        te = session.retrieve(MongoLongIdEntity, te.id)
        assert te == null
		
		// check increment
		te = new MongoLongIdEntity(name:'Bob 2')
		session.persist te
		session.flush()
		
		assert te.id == 2
    }
}

class MongoLongIdEntity {
    Long id
    String name
}
