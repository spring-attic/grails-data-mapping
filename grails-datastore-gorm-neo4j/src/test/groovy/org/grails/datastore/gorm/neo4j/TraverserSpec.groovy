package org.grails.datastore.gorm.neo4j

import grails.gorm.tests.GormDatastoreSpec
import org.neo4j.graphdb.Traverser
import org.neo4j.graphdb.StopEvaluator
import org.neo4j.graphdb.ReturnableEvaluator
import org.neo4j.graphdb.Direction
import org.neo4j.graphdb.TraversalPosition
import grails.gorm.tests.Person

/**
 * check the traverser extension
 */
class TraverserSpec extends GormDatastoreSpec {


    def "test static traversing"() {
        given:
        new Person(lastName: "person1").save()
        new Person(lastName: "person2").save()

        when:
        def traverserResult = Person.traverseStatic(Traverser.Order.BREADTH_FIRST, StopEvaluator.END_OF_GRAPH, ReturnableEvaluator.ALL_BUT_START_NODE,
                GrailsRelationshipTypes.INSTANCE, Direction.BOTH, GrailsRelationshipTypes.SUBREFERENCE, Direction.BOTH)
        def size = traverserResult.size()

        then:

        size == Person.traverseStatic(StopEvaluator.END_OF_GRAPH, ReturnableEvaluator.ALL_BUT_START_NODE,
            GrailsRelationshipTypes.INSTANCE, Direction.BOTH, GrailsRelationshipTypes.SUBREFERENCE, Direction.BOTH).size()

        size+1 == Person.traverseStatic(Traverser.Order.BREADTH_FIRST,
                { TraversalPosition p -> false },
                { TraversalPosition p -> true },
            GrailsRelationshipTypes.INSTANCE, Direction.BOTH, GrailsRelationshipTypes.SUBREFERENCE, Direction.BOTH).size()

        size+1 == Person.traverseStatic(
                { TraversalPosition p -> false },
                { TraversalPosition p -> true },
            GrailsRelationshipTypes.INSTANCE, Direction.BOTH, GrailsRelationshipTypes.SUBREFERENCE, Direction.BOTH).size()

        size+1 == Person.traverseStatic(
                { TraversalPosition p -> false },
                { TraversalPosition p -> true } ).size()

        Person.count() == Person.traverseStatic(
                { TraversalPosition p -> false },
                { TraversalPosition p -> p.currentNode().getProperty("__type__",null)==Person.class.name } ).size()

        Person.count()+2 == Person.traverseStatic( // +2: referenceNode + self (aka subreferenceNode)
                        { TraversalPosition p -> true },
                        { TraversalPosition p -> true } ).size()
    }

    def "test instance based traversing"() {
        given:
        def person = new Person(lastName: "person1")
        person.save()
        new Person(lastName: "person2").save()

        when:
        def traverserResult = person.traverse(Traverser.Order.BREADTH_FIRST, StopEvaluator.END_OF_GRAPH, ReturnableEvaluator.ALL_BUT_START_NODE,
                GrailsRelationshipTypes.INSTANCE, Direction.BOTH, GrailsRelationshipTypes.SUBREFERENCE, Direction.BOTH)
        def size = traverserResult.size()

        then:

        size == person.traverse(StopEvaluator.END_OF_GRAPH, ReturnableEvaluator.ALL_BUT_START_NODE,
            GrailsRelationshipTypes.INSTANCE, Direction.BOTH, GrailsRelationshipTypes.SUBREFERENCE, Direction.BOTH).size()

        size+1 == person.traverse(Traverser.Order.BREADTH_FIRST,
                { TraversalPosition p -> false },
                { TraversalPosition p -> true },
            GrailsRelationshipTypes.INSTANCE, Direction.BOTH, GrailsRelationshipTypes.SUBREFERENCE, Direction.BOTH).size()

        size+1 == person.traverse(
                { TraversalPosition p -> false },
                { TraversalPosition p -> true },
            GrailsRelationshipTypes.INSTANCE, Direction.BOTH, GrailsRelationshipTypes.SUBREFERENCE, Direction.BOTH).size()

        size+1 == person.traverse(
                { TraversalPosition p -> false },
                { TraversalPosition p -> true } ).size()

        Person.count() == person.traverse(
                { TraversalPosition p -> false },
                { TraversalPosition p -> p.currentNode().getProperty("__type__",null)==Person.class.name } ).size()

        2 == person.traverse(
                        { TraversalPosition p -> true },
                        { TraversalPosition p -> true } ).size()

    }

}
