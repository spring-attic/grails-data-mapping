/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.mongo.query;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Pattern;

import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.core.SessionImplementor;
import org.grails.datastore.mapping.document.config.Attribute;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.engine.internal.MappingUtils;
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller;
import org.grails.datastore.mapping.model.EmbeddedPersistentEntity;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.*;
import org.grails.datastore.mapping.mongo.MongoSession;
import org.grails.datastore.mapping.mongo.config.MongoAttribute;
import org.grails.datastore.mapping.mongo.config.MongoCollection;
import org.grails.datastore.mapping.mongo.engine.MongoEntityPersister;
import org.grails.datastore.mapping.query.AssociationQuery;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.Restrictions;
import org.grails.datastore.mapping.query.api.QueryArgumentsAware;
import org.grails.datastore.mapping.query.projections.ManualProjections;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.mongodb.core.DbCallback;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

/**
 * A {@link org.grails.datastore.mapping.query.Query} implementation for the Mongo document store.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class MongoQuery extends Query implements QueryArgumentsAware {

    private static Map<Class, QueryHandler> queryHandlers = new HashMap<Class, QueryHandler>();
    private static Map<Class, QueryHandler> negatedHandlers = new HashMap<Class, QueryHandler>();

    public static final String MONGO_IN_OPERATOR = "$in";
    public static final String MONGO_OR_OPERATOR = "$or";
    public static final String MONGO_GTE_OPERATOR = "$gte";
    public static final String MONGO_LTE_OPERATOR = "$lte";
    public static final String MONGO_GT_OPERATOR = "$gt";
    public static final String MONGO_LT_OPERATOR = "$lt";
    public static final String MONGO_NE_OPERATOR = "$ne";
    public static final String MONGO_NIN_OPERATOR = "$nin";
    public static final String MONGO_ID_REFERENCE_SUFFIX = ".$id";
    public static final String MONGO_WHERE_OPERATOR = "$where";

    private static final String MONGO_THIS_PREFIX = "this.";
    public static final String HINT_ARGUMENT = "hint";

    private Map queryArguments = Collections.emptyMap();

    public static final String NEAR_OEPRATOR = "$near";

    public static final String BOX_OPERATOR = "$box";

    public static final String WITHIN_OPERATOR = "$within";

    public static final String CENTER_OPERATOR = "$center";

    static {
        queryHandlers.put(IdEquals.class, new QueryHandler<IdEquals>() {
            public void handle(PersistentEntity entity, IdEquals criterion, DBObject query) {
                query.put(MongoEntityPersister.MONGO_ID_FIELD, criterion.getValue());
            }
        });

        queryHandlers.put(AssociationQuery.class, new QueryHandler<AssociationQuery>() {
            public void handle(PersistentEntity entity, AssociationQuery criterion, DBObject query) {
                Association<?> association = criterion.getAssociation();
                PersistentEntity associatedEntity = association.getAssociatedEntity();
                if(association instanceof EmbeddedCollection) {
                    BasicDBObject associationCollectionQuery = new BasicDBObject();
                    populateMongoQuery(associatedEntity, associationCollectionQuery, criterion.getCriteria());
                    BasicDBObject collectionQuery = new BasicDBObject("$elemMatch", associationCollectionQuery);
                    String propertyKey = getPropertyName(entity, association.getName());
                    query.put(propertyKey, collectionQuery);
                }
                else if(associatedEntity instanceof EmbeddedPersistentEntity || association instanceof Embedded ) {
                    BasicDBObject associatedEntityQuery = new BasicDBObject();
                    populateMongoQuery(associatedEntity, associatedEntityQuery, criterion.getCriteria());
                    for (String property : associatedEntityQuery.keySet()) {
                        String propertyKey = getPropertyName(entity, association.getName());
                        query.put(propertyKey + '.' + property, associatedEntityQuery.get(property));
                    }

                }
                else {
                    throw new UnsupportedOperationException("Join queries are not supported by MongoDB");
                }
            }
        });

        queryHandlers.put(Equals.class, new QueryHandler<Equals>() {
            public void handle(PersistentEntity entity, Equals criterion, DBObject query) {
                String propertyName = getPropertyName(entity, criterion);
                Object value = criterion.getValue();
                MongoEntityPersister.setDBObjectValue(query, propertyName, value, entity.getMappingContext());
            }
        });

        queryHandlers.put(IsNull.class, new QueryHandler<IsNull>() {
            public void handle(PersistentEntity entity, IsNull criterion, DBObject query) {
                queryHandlers.get(Equals.class).handle(entity, new Equals(criterion.getProperty(), null), query);
            }
        });
        queryHandlers.put(IsNotNull.class, new QueryHandler<IsNotNull>() {
            public void handle(PersistentEntity entity, IsNotNull criterion, DBObject query) {
                queryHandlers.get(NotEquals.class).handle(entity, new NotEquals(criterion.getProperty(), null), query);
            }
        });
        queryHandlers.put(EqualsProperty.class, new QueryHandler<EqualsProperty>() {
            public void handle(PersistentEntity entity, EqualsProperty criterion, DBObject query) {
                String propertyName = getPropertyName(entity, criterion);
                String otherPropertyName = getPropertyName(entity, criterion.getOtherProperty());
                addWherePropertyComparison(query, propertyName, otherPropertyName, "==");
            }
        });
        queryHandlers.put(NotEqualsProperty.class, new QueryHandler<NotEqualsProperty>() {
            public void handle(PersistentEntity entity, NotEqualsProperty criterion, DBObject query) {
                String propertyName = getPropertyName(entity, criterion);
                String otherPropertyName = getPropertyName(entity, criterion.getOtherProperty());
                addWherePropertyComparison(query, propertyName, otherPropertyName, "!=");
            }
        });
        queryHandlers.put(GreaterThanProperty.class, new QueryHandler<GreaterThanProperty>() {
            public void handle(PersistentEntity entity, GreaterThanProperty criterion, DBObject query) {
                String propertyName = getPropertyName(entity, criterion);
                String otherPropertyName = getPropertyName(entity, criterion.getOtherProperty());
                addWherePropertyComparison(query, propertyName, otherPropertyName, ">");
            }
        });
        queryHandlers.put(LessThanProperty.class, new QueryHandler<LessThanProperty>() {
            public void handle(PersistentEntity entity, LessThanProperty criterion, DBObject query) {
                String propertyName = getPropertyName(entity, criterion);
                String otherPropertyName = getPropertyName(entity, criterion.getOtherProperty());
                addWherePropertyComparison(query, propertyName, otherPropertyName, "<");
            }
        });
        queryHandlers.put(GreaterThanEqualsProperty.class, new QueryHandler<GreaterThanEqualsProperty>() {
            public void handle(PersistentEntity entity, GreaterThanEqualsProperty criterion, DBObject query) {
                String propertyName = getPropertyName(entity, criterion);
                String otherPropertyName = getPropertyName(entity, criterion.getOtherProperty());
                addWherePropertyComparison(query, propertyName, otherPropertyName, ">=");
            }
        });
        queryHandlers.put(LessThanEqualsProperty.class, new QueryHandler<LessThanEqualsProperty>() {
            public void handle(PersistentEntity entity, LessThanEqualsProperty criterion, DBObject query) {
                String propertyName = getPropertyName(entity, criterion);
                String otherPropertyName = getPropertyName(entity, criterion.getOtherProperty());
                addWherePropertyComparison(query, propertyName, otherPropertyName, "<=");
            }
        });


        queryHandlers.put(NotEquals.class, new QueryHandler<NotEquals>() {
            public void handle(PersistentEntity entity, NotEquals criterion, DBObject query) {
                String propertyName = getPropertyName(entity, criterion);
                DBObject notEqualQuery = getOrCreatePropertyQuery(query, propertyName);
                MongoEntityPersister.setDBObjectValue(notEqualQuery, MONGO_NE_OPERATOR, criterion.getValue(), entity.getMappingContext());

                query.put(propertyName, notEqualQuery);
            }
        });

        queryHandlers.put(Like.class, new QueryHandler<Like>() {
            public void handle(PersistentEntity entity, Like like, DBObject query) {
                handleLike(entity, like, query, true);
            }
        });

      queryHandlers.put(ILike.class, new QueryHandler<ILike>() {
            public void handle(PersistentEntity entity, ILike like, DBObject query) {
                handleLike(entity, like, query, false);
            }
        });

        queryHandlers.put(RLike.class, new QueryHandler<RLike>() {
            public void handle(PersistentEntity entity, RLike like, DBObject query) {
                Object value = like.getValue();
                if (value == null) value = "null";
                final String expr = value.toString();
                Pattern regex = Pattern.compile(expr);
                String propertyName = getPropertyName(entity, like);
                query.put(propertyName, regex);
            }
        });

        queryHandlers.put(In.class, new QueryHandler<In>() {
            @SuppressWarnings("unchecked")
            public void handle(PersistentEntity entity, In in, DBObject query) {
                DBObject inQuery = new BasicDBObject();
                List ids = new ArrayList();
                for (Object value : in.getValues()) {
                    PersistentEntity pe = entity.getMappingContext().getPersistentEntity(
                            value.getClass().getName());
                    if (value == null || pe == null) {
                        ids.add(value);
                    }
                    else {
                        ids.add(new EntityAccess(pe, value).getIdentifier());
                    }
                }
                inQuery.put(MONGO_IN_OPERATOR, ids);
                String propertyName = getPropertyName(entity, in);
                query.put(propertyName, inQuery);
            }
        });

        queryHandlers.put(Near.class, new QueryHandler<Near>() {
            public void handle(PersistentEntity entity, Near near, DBObject query) {
                DBObject nearQuery = new BasicDBObject();
                MongoEntityPersister.setDBObjectValue(nearQuery, NEAR_OEPRATOR, near.getValues(), entity.getMappingContext());
                String propertyName = getPropertyName(entity, near);
                query.put(propertyName, nearQuery);
            }
        });

        queryHandlers.put(WithinBox.class, new QueryHandler<WithinBox>() {
            public void handle(PersistentEntity entity, WithinBox withinBox, DBObject query) {
                DBObject nearQuery = new BasicDBObject();
                DBObject box = new BasicDBObject();
                MongoEntityPersister.setDBObjectValue(box, BOX_OPERATOR, withinBox.getValues(), entity.getMappingContext());
                nearQuery.put(WITHIN_OPERATOR, box);
                String propertyName = getPropertyName(entity, withinBox);
                query.put(propertyName, nearQuery);
            }
        });

        queryHandlers.put(WithinCircle.class, new QueryHandler<WithinCircle>() {
            public void handle(PersistentEntity entity, WithinCircle withinCentre, DBObject query) {
                DBObject nearQuery = new BasicDBObject();
                DBObject center = new BasicDBObject();
                MongoEntityPersister.setDBObjectValue(center, CENTER_OPERATOR, withinCentre.getValues(), entity.getMappingContext());
                nearQuery.put(WITHIN_OPERATOR, center);
                String propertyName = getPropertyName(entity, withinCentre);
                query.put(propertyName, nearQuery);
            }
        });

        queryHandlers.put(Between.class, new QueryHandler<Between>() {
            public void handle(PersistentEntity entity, Between between, DBObject query) {
                DBObject betweenQuery = new BasicDBObject();
                MongoEntityPersister.setDBObjectValue(betweenQuery, MONGO_GTE_OPERATOR, between.getFrom(), entity.getMappingContext());
                MongoEntityPersister.setDBObjectValue(betweenQuery, MONGO_LTE_OPERATOR, between.getTo(), entity.getMappingContext());
                String propertyName = getPropertyName(entity, between);
                query.put(propertyName, betweenQuery);
            }
        });

        queryHandlers.put(GreaterThan.class, new QueryHandler<GreaterThan>() {
            public void handle(PersistentEntity entity, GreaterThan criterion, DBObject query) {
                String propertyName = getPropertyName(entity, criterion);
                DBObject greaterThanQuery = getOrCreatePropertyQuery(query, propertyName);
                MongoEntityPersister.setDBObjectValue(greaterThanQuery, MONGO_GT_OPERATOR, criterion.getValue(), entity.getMappingContext());

                query.put(propertyName, greaterThanQuery);
            }
        });

        queryHandlers.put(GreaterThanEquals.class, new QueryHandler<GreaterThanEquals>() {
            public void handle(PersistentEntity entity, GreaterThanEquals criterion, DBObject query) {
                String propertyName = getPropertyName(entity, criterion);
                DBObject greaterThanQuery = getOrCreatePropertyQuery(query, propertyName);
                MongoEntityPersister.setDBObjectValue(greaterThanQuery, MONGO_GTE_OPERATOR, criterion.getValue(), entity.getMappingContext());

                query.put(propertyName, greaterThanQuery);
            }
        });

        queryHandlers.put(LessThan.class, new QueryHandler<LessThan>() {
            public void handle(PersistentEntity entity, LessThan criterion, DBObject query) {
                String propertyName = getPropertyName(entity, criterion);
                DBObject lessThanQuery = getOrCreatePropertyQuery(query, propertyName);
                MongoEntityPersister.setDBObjectValue(lessThanQuery, MONGO_LT_OPERATOR, criterion.getValue(), entity.getMappingContext());

                query.put(propertyName, lessThanQuery);
            }
        });

        queryHandlers.put(LessThanEquals.class, new QueryHandler<LessThanEquals>() {
            public void handle(PersistentEntity entity, LessThanEquals criterion, DBObject query) {
                String propertyName = getPropertyName(entity, criterion);
                DBObject lessThanQuery = getOrCreatePropertyQuery(query, propertyName);
                MongoEntityPersister.setDBObjectValue(lessThanQuery, MONGO_LTE_OPERATOR, criterion.getValue(), entity.getMappingContext());

                query.put(propertyName, lessThanQuery);
            }
        });

        queryHandlers.put(Conjunction.class, new QueryHandler<Conjunction>() {
            public void handle(PersistentEntity entity, Conjunction criterion, DBObject query) {
                populateMongoQuery(entity, query, criterion);
            }
        });

        queryHandlers.put(Negation.class, new QueryHandler<Negation>() {
            @SuppressWarnings("unchecked")
            public void handle(PersistentEntity entity, Negation criteria, DBObject query) {
                for (Criterion criterion : criteria.getCriteria()) {
                    final QueryHandler queryHandler = negatedHandlers.get(criterion.getClass());
                    if (queryHandler != null) {
                        queryHandler.handle(entity, criterion, query);
                    }
                    else {
                        throw new UnsupportedOperationException("Query of type "+criterion.getClass().getSimpleName()+" cannot be negated");
                    }
                }
            }
        });

        queryHandlers.put(Disjunction.class, new QueryHandler<Disjunction>() {
            @SuppressWarnings("unchecked")
            public void handle(PersistentEntity entity, Disjunction criterion, DBObject query) {
                List orList = new ArrayList();
                for (Criterion subCriterion : criterion.getCriteria()) {
                    final QueryHandler queryHandler = queryHandlers.get(subCriterion.getClass());
                    if (queryHandler != null) {
                        DBObject dbo = new BasicDBObject();
                        queryHandler.handle(entity, subCriterion, dbo);
                        orList.add(dbo);
                    }
                }
                query.put(MONGO_OR_OPERATOR, orList);
            }
        });

        negatedHandlers.put(Equals.class, new QueryHandler<Equals>() {
            @SuppressWarnings("unchecked")
            public void handle(PersistentEntity entity, Equals criterion, DBObject query) {
                queryHandlers.get(NotEquals.class).handle(entity, Restrictions.ne(criterion.getProperty(), criterion.getValue()), query);
            }
        });

        negatedHandlers.put(NotEquals.class, new QueryHandler<NotEquals>() {
            @SuppressWarnings("unchecked")
            public void handle(PersistentEntity entity, NotEquals criterion, DBObject query) {
                queryHandlers.get(Equals.class).handle(entity, Restrictions.eq(criterion.getProperty(), criterion.getValue()), query);
            }
        });

        negatedHandlers.put(In.class, new QueryHandler<In>() {
            public void handle(PersistentEntity entity, In in, DBObject query) {
                String property = getPropertyName(entity, in);
                DBObject inQuery = getOrCreatePropertyQuery(query, property);
                inQuery.put(MONGO_NIN_OPERATOR, in.getValues());
                query.put(property, inQuery);
            }
        });

        negatedHandlers.put(Between.class, new QueryHandler<Between>() {
            public void handle(PersistentEntity entity, Between between, DBObject query) {
                String property = getPropertyName(entity, between);
                DBObject betweenQuery = getOrCreatePropertyQuery(query, property);
                betweenQuery.put(MONGO_LTE_OPERATOR, between.getFrom());
                betweenQuery.put(MONGO_GTE_OPERATOR, between.getTo());
                query.put(property, betweenQuery);
            }
        });

        negatedHandlers.put(GreaterThan.class, new QueryHandler<GreaterThan>() {
            @SuppressWarnings("unchecked")
            public void handle(PersistentEntity entity, GreaterThan criterion, DBObject query) {
                queryHandlers.get(LessThan.class).handle(entity, Restrictions.lt(criterion.getProperty(), criterion.getValue()), query);
            }
        });

        negatedHandlers.put(GreaterThanEquals.class, new QueryHandler<GreaterThanEquals>() {
            @SuppressWarnings("unchecked")
            public void handle(PersistentEntity entity, GreaterThanEquals criterion, DBObject query) {
                queryHandlers.get(LessThanEquals.class).handle(entity, Restrictions.lte(criterion.getProperty(), criterion.getValue()), query);
            }
        });

        negatedHandlers.put(LessThan.class, new QueryHandler<LessThan>() {
            @SuppressWarnings("unchecked")
            public void handle(PersistentEntity entity, LessThan criterion, DBObject query) {
                queryHandlers.get(GreaterThan.class).handle(entity, Restrictions.gt(criterion.getProperty(), criterion.getValue()), query);
            }
        });

        negatedHandlers.put(LessThanEquals.class, new QueryHandler<LessThanEquals>() {
            @SuppressWarnings("unchecked")
            public void handle(PersistentEntity entity, LessThanEquals criterion, DBObject query) {
                queryHandlers.get(GreaterThanEquals.class).handle(entity, Restrictions.gte(criterion.getProperty(), criterion.getValue()), query);
            }
        });
    }

    private static DBObject getOrCreatePropertyQuery(DBObject query, String propertyName) {
        Object existing = query.get(propertyName);
        DBObject queryObject = existing instanceof DBObject ? (DBObject) existing : null;
        if(queryObject == null) {
            queryObject = new BasicDBObject();
        }
        return queryObject;
    }


    private static void addWherePropertyComparison(DBObject query, String propertyName, String otherPropertyName, String operator) {
        query.put(MONGO_WHERE_OPERATOR, new StringBuilder(MONGO_THIS_PREFIX).append(propertyName).append(operator).append(MONGO_THIS_PREFIX).append(otherPropertyName).toString());
    }

    private static void handleLike(PersistentEntity entity, Like like, DBObject query, boolean caseSensitive) {
        Object value = like.getValue();
        if (value == null) value = "null";

        String[] array = value.toString().split("%", -1);
        for (int i=0; i<array.length; i++) {
            array[i] = Pattern.quote(array[i]);
        }
        String expr = StringUtils.arrayToDelimitedString(array, ".*");
        if (!expr.startsWith(".*")) {
            expr = '^'+expr;
        }
        if (!expr.endsWith(".*")) {
            expr = expr+'$';
        }

        Pattern regex = caseSensitive ? Pattern.compile(expr) : Pattern.compile(expr, Pattern.CASE_INSENSITIVE);
        String propertyName = getPropertyName(entity, like);
        query.put(propertyName, regex);
    }

    private MongoSession mongoSession;
    private MongoEntityPersister mongoEntityPersister;
    private ManualProjections manualProjections;

    public MongoQuery(MongoSession session, PersistentEntity entity) {
        super(session, entity);
        this.mongoSession = session;
        this.manualProjections = new ManualProjections(entity);
        this.mongoEntityPersister = (MongoEntityPersister) session.getPersister(entity);
    }

    @Override
    protected void flushBeforeQuery() {
        // with Mongo we only flush the session if a transaction is not active to allow for session-managed transactions
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            super.flushBeforeQuery();
        }
    }

    /**
     * Gets the Mongo query for this query instance
     *
     * @return The Mongo query
     */
    public DBObject getMongoQuery() {
        DBObject query = createQueryObject(entity);
        populateMongoQuery(entity, query,criteria);
        return query;
    }

    @SuppressWarnings("hiding")
    @Override
    protected List executeQuery(final PersistentEntity entity, final Junction criteria) {
        final MongoTemplate template = mongoSession.getMongoTemplate(entity);

        return template.execute(new DbCallback<List>() {
            @SuppressWarnings("unchecked")
            public List doInDB(DB db) throws MongoException, DataAccessException {

                final DBCollection collection = db.getCollection(mongoEntityPersister.getCollectionName(entity));
                if (uniqueResult) {
                    final DBObject dbObject;
                    if (criteria.isEmpty()) {
                        if (entity.isRoot()) {
                            dbObject = collection.findOne();
                        }
                        else {
                            dbObject = collection.findOne(new BasicDBObject(
                                  MongoEntityPersister.MONGO_CLASS_FIELD, entity.getDiscriminator()));
                        }
                    }
                    else {
                        dbObject = collection.findOne(getMongoQuery());
                    }
                    return wrapObjectResultInList(createObjectFromDBObject(dbObject));
                }

                DBCursor cursor = null;
                DBObject query = createQueryObject(entity);

                final List<Projection> projectionList = projections().getProjectionList();
                if (projectionList.isEmpty()) {
                    cursor = executeQuery(entity, criteria, collection, query);
                    return (List)new MongoResultList(cursor, mongoEntityPersister).clone();
                }

                List projectedResults = new ArrayList();
                for (Projection projection : projectionList) {
                    if (projection instanceof CountProjection) {
                        // For some reason the below doesn't return the expected result whilst executing the query and returning the cursor does
                        //projectedResults.add(collection.getCount(query));
                        if(cursor == null)
                            cursor = executeQuery(entity, criteria, collection, query);
                        projectedResults.add(cursor.size());
                    }
                    else if (projection instanceof MinProjection) {
                        if(cursor == null)
                            cursor = executeQuery(entity, criteria, collection, query);
                        MinProjection mp = (MinProjection) projection;

                        MongoResultList results = new MongoResultList(cursor, mongoEntityPersister);
                        projectedResults.add(manualProjections.min((Collection) results.clone(), getPropertyName(entity, mp.getPropertyName())));
                    }
                    else if (projection instanceof MaxProjection) {
                        if(cursor == null)
                            cursor = executeQuery(entity, criteria, collection, query);
                        MaxProjection mp = (MaxProjection) projection;

                        MongoResultList results = new MongoResultList(cursor, mongoEntityPersister);
                        projectedResults.add(manualProjections.max((Collection) results.clone(), getPropertyName(entity, mp.getPropertyName())));
                    }
                    else if(projection instanceof CountDistinctProjection) {
                        if(cursor == null)
                            cursor = executeQuery(entity, criteria, collection, query);
                        CountDistinctProjection mp = (CountDistinctProjection) projection;

                        MongoResultList results = new MongoResultList(cursor, mongoEntityPersister);
                        projectedResults.add(manualProjections.countDistinct((Collection) results.clone(), getPropertyName(entity, mp.getPropertyName())));

                    }
                    else if ((projection instanceof PropertyProjection) || (projection instanceof IdProjection)) {
                        final PersistentProperty persistentProperty;
                        final String propertyName;
                        if (projection instanceof IdProjection) {
                            persistentProperty = entity.getIdentity();
                            propertyName = MongoEntityPersister.MONGO_ID_FIELD;
                        }
                        else {
                            PropertyProjection pp = (PropertyProjection) projection;
                            persistentProperty = entity.getPropertyByName(pp.getPropertyName());
                            propertyName = getPropertyName(entity, persistentProperty.getName());
                        }
                        if (persistentProperty != null) {
                            populateMongoQuery(entity, query, criteria);


                            List propertyResults = null;
                            if(max > -1) {
                                // if there is a limit then we have to do a manual projection since the MongoDB driver doesn't support limits and distinct together
                                cursor = executeQueryAndApplyPagination(collection, query);
                                propertyResults = manualProjections.property(new MongoResultList(cursor, mongoEntityPersister), propertyName);
                            }
                            else {

                                propertyResults = collection.distinct(propertyName, query);
                            }


                            if (persistentProperty instanceof ToOne) {
                                Association a = (Association) persistentProperty;
                                propertyResults = session.retrieveAll(a.getAssociatedEntity().getJavaClass(), propertyResults);
                            }

                            if (projectedResults.size() == 0 && projectionList.size() == 1) {
                                return propertyResults;
                            }
                            projectedResults.add(propertyResults);
                        }
                        else {
                            throw new InvalidDataAccessResourceUsageException("Cannot use [" +
                                    projection.getClass().getSimpleName() +
                                    "] projection on non-existent property: " + propertyName);
                        }
                    }
                }

                return projectedResults;
            }

            protected DBCursor executeQuery(final PersistentEntity entity,
                    final Junction criteria, final DBCollection collection,
                    DBObject query) {
                final DBCursor cursor;
                if (criteria.isEmpty()) {
                    cursor = executeQueryAndApplyPagination(collection, query);
                }
                else {
                    populateMongoQuery(entity, query, criteria);
                    cursor = executeQueryAndApplyPagination(collection, query);
                }

                if(queryArguments != null) {
                    if(queryArguments.containsKey(HINT_ARGUMENT)) {
                        Object hint = queryArguments.get(HINT_ARGUMENT);
                        if(hint instanceof Map) {
                            cursor.hint(new BasicDBObject((Map)hint));
                        }
                        else if (hint != null){
                            cursor.hint(hint.toString());
                        }

                    }
                }
                return cursor;
            }

            protected DBCursor executeQueryAndApplyPagination(
                    final DBCollection collection, DBObject query) {
                final DBCursor cursor;
                cursor = collection.find(query);
                if (offset > 0) {
                    cursor.skip(offset);
                }
                if (max > -1) {
                    cursor.limit(max);
                }

                if (!orderBy.isEmpty()) {
                    DBObject orderObject = new BasicDBObject();
                    for (Order order : orderBy) {
                        String property = order.getProperty();
                        property = getPropertyName(entity, property);
                        orderObject.put(property, order.getDirection() == Order.Direction.DESC ? -1 : 1);
                    }
                    cursor.sort(orderObject);
                }
                else {
                    MongoCollection coll = (MongoCollection) entity.getMapping().getMappedForm();
                    if(coll != null && coll.getSort() != null) {
                        DBObject orderObject = new BasicDBObject();
                        Order order = coll.getSort();
                        String property = order.getProperty();
                        property = getPropertyName(entity, property);
                        orderObject.put(property, order.getDirection() == Order.Direction.DESC ? -1 : 1);
                        cursor.sort(orderObject);
                    }
                    
                }

                return cursor;
            }
        });
    }

    private DBObject createQueryObject(PersistentEntity persistentEntity) {
        DBObject query;
        if (persistentEntity.isRoot()) {
            query = new BasicDBObject();
        }
        else {
            query = new BasicDBObject(MongoEntityPersister.MONGO_CLASS_FIELD, persistentEntity.getDiscriminator());
        }
        return query;
    }

    @SuppressWarnings("unchecked")
    public static void populateMongoQuery(PersistentEntity entity, DBObject query, Junction criteria) {

        List disjunction = null;
        if (criteria instanceof Disjunction) {
            disjunction = new ArrayList();
            query.put(MONGO_OR_OPERATOR, disjunction);
        }
        for (Criterion criterion : criteria.getCriteria()) {
            final QueryHandler queryHandler = queryHandlers.get(criterion.getClass());
            if (queryHandler != null) {
                DBObject dbo = query;
                if (disjunction != null) {
                    dbo = new BasicDBObject();
                    disjunction.add(dbo);
                }

                if (criterion instanceof PropertyCriterion) {
                    PropertyCriterion pc = (PropertyCriterion) criterion;
                    PersistentProperty property = entity.getPropertyByName(pc.getProperty());
                    if (property instanceof Custom) {
                        CustomTypeMarshaller customTypeMarshaller = ((Custom) property).getCustomTypeMarshaller();
                        customTypeMarshaller.query(property, pc, query);
                        continue;
                    }
                }
                queryHandler.handle(entity, criterion, dbo);
            }
            else {
                throw new InvalidDataAccessResourceUsageException("Queries of type "+criterion.getClass().getSimpleName()+" are not supported by this implementation");
            }
        }
    }

    protected static String getPropertyName(PersistentEntity entity,
            PropertyNameCriterion criterion) {
        String propertyName = criterion.getProperty();
        return getPropertyName(entity, propertyName);
    }

    private static String getPropertyName(PersistentEntity entity, String propertyName) {
        if (entity.isIdentityName(propertyName)) {
            propertyName = MongoEntityPersister.MONGO_ID_FIELD;
        }
        else {
            PersistentProperty property = entity.getPropertyByName(propertyName);
            if (property != null) {
                propertyName = MappingUtils.getTargetKey(property);
                if(property instanceof ToOne && !(property instanceof Embedded)) {
                    ToOne association = (ToOne) property;
                    MongoAttribute attr = (MongoAttribute) association.getMapping().getMappedForm();
                    boolean isReference = attr == null || attr.isReference();
                    if(isReference)
                        propertyName = propertyName + MONGO_ID_REFERENCE_SUFFIX;
                }
            }
        }

        return propertyName;
    }

    private Object createObjectFromDBObject(DBObject dbObject) {
        final Object id = dbObject.get(MongoEntityPersister.MONGO_ID_FIELD);
        return mongoEntityPersister.createObjectFromNativeEntry(getEntity(), (Serializable) id, dbObject);
    }

    @SuppressWarnings("unchecked")
    private List wrapObjectResultInList(Object object) {
        List result = new ArrayList();
        result.add(object);
        return result;
    }

    /**
     * Geospacial query for values near the given two dimensional list
     *
     * @param property The property
     * @param value A two dimensional list of values
     * @return this
     */
    public Query near(String property, List value) {
        add(new Near(property, value));
        return this;
    }

    /**
     * Geospacial query for values within a given box. A box is defined as a multi-dimensional list in the form
     *
     * [[40.73083, -73.99756], [40.741404,  -73.988135]]
     *
     * @param property The property
     * @param value A multi-dimensional list of values
     * @return This query
     */
    public Query withinBox(String property, List value) {
        add(new WithinBox(property, value));
        return this;
    }

    /**
     * Geospacial query for values within a given circle. A circle is defined as a multi-dimensial list containing the position of the center and the radius:
     *
     * [[50, 50], 10]
     *
     * @param property The property
     * @param value A multi-dimensional list of values
     * @return This query
     */
    public Query withinCircle(String property, List value) {
        add(new WithinBox(property, value));
        return this;
    }

    /**
     * @param arguments The query arguments
     */
    public void setArguments(Map arguments) {
        this.queryArguments = arguments;
    }

    /**
     * Used for Geospacial querying
     *
     * @author Graeme Rocher
     * @since 1.0
     */
    public static class Near extends PropertyCriterion {

        public Near(String name, List value) {
            super(name, value);
        }

        public List getValues() {
            return (List) getValue();
        }

        public void setValue(List value) {
            this.value = value;
        }
    }

    /**
     * Used for Geospacial querying of boxes
     *
     * @author Graeme Rocher
     * @since 1.0
     */
    public static class WithinBox extends PropertyCriterion {

        public WithinBox(String name, List value) {
            super(name, value);
        }

        public List getValues() {
            return (List) getValue();
        }

        public void setValue(List matrix) {
            this.value = matrix;
        }
    }

    /**
     * Used for Geospacial querying of circles
     *
     * @author Graeme Rocher
     * @since 1.0
     */
    public static class WithinCircle extends PropertyCriterion {

        public WithinCircle(String name, List value) {
            super(name, value);
        }

        public List getValues() {
            return (List) getValue();
        }

        public void setValue(List matrix) {
            this.value = matrix;
        }
    }

    private static interface QueryHandler<T> {
        public void handle(PersistentEntity entity, T criterion, DBObject query);
    }

    @SuppressWarnings("serial")
    public static class MongoResultList extends ArrayList {

        private MongoEntityPersister mongoEntityPersister;

        @SuppressWarnings("unchecked")
        public MongoResultList(DBCursor cursor, MongoEntityPersister mongoEntityPersister) {
            super.addAll(cursor.toArray());
            this.mongoEntityPersister = mongoEntityPersister;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object get(int index) {
            Object object = super.get(index);

            if (object instanceof DBObject) {
                object = convertDBObject(object);
                set(index, object);
            }

            return  object;
        }

        protected Object convertDBObject(Object object) {
            final DBObject dbObject = (DBObject) object;
            Object id = dbObject.get(MongoEntityPersister.MONGO_ID_FIELD);
            Object instance = ((SessionImplementor)mongoEntityPersister.getSession()).getCachedInstance(
                    mongoEntityPersister.getPersistentEntity().getJavaClass(), (Serializable)id);
            if (instance == null) {
                instance = mongoEntityPersister.createObjectFromNativeEntry(
                    mongoEntityPersister.getPersistentEntity(), (Serializable) id, dbObject);
            }
            return instance;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object clone() {
            List arrayList = new ArrayList();
            for (Object object : this) {
                if (object instanceof DBObject) {
                    object = convertDBObject(object);
                }
                arrayList.add(object);
            }
            return arrayList;
        }
    }
}
