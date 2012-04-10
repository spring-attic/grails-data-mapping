package org.grails.datastore.gorm

import grails.gorm.tests.NamedQuerySpec
import org.junit.runners.Suite.SuiteClasses
import org.junit.runners.Suite
import org.junit.runner.RunWith
import grails.gorm.tests.WithTransactionSpec
import grails.gorm.tests.DomainEventsSpec
import grails.gorm.tests.NegationSpec
import grails.gorm.tests.UpdateWithProxyPresentSpec
import grails.gorm.tests.QueryAfterPropertyChangeSpec
import grails.gorm.tests.AttachMethodSpec
import grails.gorm.tests.CriteriaBuilderSpec
import grails.gorm.tests.GroovyProxySpec
import grails.gorm.tests.ListOrderBySpec
import grails.gorm.tests.FindByMethodSpec
import grails.gorm.tests.InheritanceSpec
import grails.gorm.tests.CircularOneToManySpec
import grails.gorm.tests.ProxyLoadingSpec
import grails.gorm.tests.DetachedCriteriaSpec
import grails.gorm.tests.WhereMethodSpec
import grails.gorm.tests.EnumSpec
import grails.gorm.tests.OrderBySpec

/**
 * @author graemerocher
 */
@RunWith(Suite)
@SuiteClasses([
    WhereMethodSpec,
    ProxyLoadingSpec,
    QueryAfterPropertyChangeSpec,
    CircularOneToManySpec,
    InheritanceSpec,
    FindByMethodSpec,
    ListOrderBySpec,
    GroovyProxySpec,
    DomainEventsSpec,
    DetachedCriteriaSpec,
    QueryAssociationSpec,
    WhereMethodSpec,
    CriteriaBuilderSpec,
    NegationSpec,
    UpdateWithProxyPresentSpec,
    AttachMethodSpec,
    WithTransactionSpec,
    EnumSpec,
    DomainEventsSpec,
    CustomStringIdentifierSpec,
    OrderBySpec
])
class SimpleMapTestSuite {
}
