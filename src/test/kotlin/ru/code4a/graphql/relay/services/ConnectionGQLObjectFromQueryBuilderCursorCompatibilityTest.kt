package ru.code4a.graphql.relay.services

import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.CriteriaQuery
import org.hibernate.Hibernate
import org.hibernate.Session
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.LazyInitializer
import org.hibernate.query.KeyedPage
import org.hibernate.query.KeyedResultList
import org.hibernate.query.Order
import org.hibernate.query.Page
import org.hibernate.query.SelectionQuery
import org.hibernate.query.SortDirection
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import ru.code4a.graphql.relay.annotations.GraphqlConnectionOrderField
import ru.code4a.graphql.relay.interfaces.GraphqlRelayDeterministicCipher
import ru.code4a.graphql.relay.interfaces.GraphqlRelayEntityGetterFactoryService
import ru.code4a.graphql.relay.interfaces.GraphqlRelayResponseObjectBuilder
import ru.code4a.graphql.relay.schema.enums.OrderTypeGQLEnum
import ru.code4a.graphql.relay.schema.objects.GraphqlNode
import ru.code4a.graphql.relay.services.containers.CursorContainer

class ConnectionGQLObjectFromQueryBuilderCursorCompatibilityTest {

  @Test
  fun `accepts cursor from subtype for query by base entity without node info`() {
    requireCursorCompatibleWithQueryEntityClass(
      queryEntityClass = SubjectDomainEntity::class.java,
      queryNodeInfo = null,
      cursorNodeInfo = nodeInfo(
        nodeId = 100,
        entityClass = IndividualDomainEntity::class.java
      )
    )
  }

  @Test
  fun `keeps strict matching for query by concrete entity`() {
    val queryNodeInfo = nodeInfo(
      nodeId = 100,
      entityClass = IndividualDomainEntity::class.java
    )

    requireCursorCompatibleWithQueryEntityClass(
      queryEntityClass = IndividualDomainEntity::class.java,
      queryNodeInfo = queryNodeInfo,
      cursorNodeInfo = queryNodeInfo
    )

    assertFailsWith<IllegalArgumentException> {
      requireCursorCompatibleWithQueryEntityClass(
        queryEntityClass = IndividualDomainEntity::class.java,
        queryNodeInfo = queryNodeInfo,
        cursorNodeInfo = nodeInfo(
          nodeId = 200,
          entityClass = LegalDomainEntity::class.java
        )
      )
    }
  }

  @Test
  fun `rejects cursor from unrelated entity when query is by base entity`() {
    assertFailsWith<IllegalArgumentException> {
      requireCursorCompatibleWithQueryEntityClass(
        queryEntityClass = SubjectDomainEntity::class.java,
        queryNodeInfo = null,
        cursorNodeInfo = nodeInfo(
          nodeId = 300,
          entityClass = UnrelatedDomainEntity::class.java
        )
      )
    }
  }

  @Test
  fun `resolves order getter on unproxied implementation when base proxy is not concrete entity instance`() {
    val proxy = hibernateProxyEntity()
    val hibernateEntityClass: Class<*> = Hibernate.getClass(proxy)

    val entityGetter =
      getCursorEntityValueGetterMethod(
        entityClass = hibernateEntityClass,
        entityValueGetter = "getName"
      )

    assertSame(CursorProxyTestEntity::class.java, hibernateEntityClass)
    assertSame(CursorProxyTestEntity::class.java, entityGetter.declaringClass)
    assertFailsWith<IllegalArgumentException> {
      entityGetter.invoke(proxy)
    }

    val value =
      getCursorOrderValue(
        entity = proxy,
        entityClass = hibernateEntityClass,
        graphqlNodeInfo = nodeInfo(
          nodeId = 400,
          entityClass = CursorProxyTestEntity::class.java
        ),
        entityFieldName = "name",
        entityValueGetter = "getName"
      )

    assertEquals("proxy-name", value)
  }

  @Suppress("UNCHECKED_CAST")
  @Test
  fun `build creates id cursor from unproxied implementation when base proxy is not concrete entity instance`() {
    val proxy = hibernateProxyEntity(id = 7, name = "proxy-name")
    val hibernateEntityClass: Class<*> = Hibernate.getClass(proxy)
    val entityGetIdMethod = hibernateEntityClass.getMethod("getId")

    assertSame(CursorProxyTestEntity::class.java, hibernateEntityClass)
    assertSame(CursorProxyTestEntity::class.java, entityGetIdMethod.declaringClass)
    assertFailsWith<IllegalArgumentException> {
      entityGetIdMethod.invoke(proxy)
    }

    val cursorCipherGraphqlNodeService =
      CursorCipherGraphqlNodeService(IdentityGraphqlRelayDeterministicCipher)

    val graphqlRelayNodeManager =
      GraphqlRelayNodeManager(NodeIdCipherGraphqlNodeService(IdentityGraphqlRelayDeterministicCipher))

    val selectionQuery =
      Mockito.mock(SelectionQuery::class.java) as SelectionQuery<CursorProxyTestBaseEntity>
    val resultPage =
      Page
        .first(1)
        .keyedBy(Order.desc(CursorProxyTestBaseEntity::class.java, "id"))
    val keyedResultList =
      KeyedResultList(
        listOf(proxy),
        listOf(listOf(7L)),
        resultPage,
        null,
        null
      )

    Mockito
      .`when`(selectionQuery.getKeyedResultList(ArgumentMatchers.any<KeyedPage<CursorProxyTestBaseEntity>>()))
      .thenReturn(keyedResultList)

    val entityManager = Mockito.mock(EntityManager::class.java)
    val session = Mockito.mock(Session::class.java)
    val query = Mockito.mock(CriteriaQuery::class.java) as CriteriaQuery<CursorProxyTestBaseEntity>

    Mockito
      .`when`(entityManager.unwrap(Session::class.java))
      .thenReturn(session)
    Mockito
      .`when`(session.createSelectionQuery(query))
      .thenReturn(selectionQuery)

    val connectionBuilder =
      ConnectionGQLObjectFromQueryBuilder(
        entityManager = entityManager,
        graphqlRelayNodeManager = graphqlRelayNodeManager,
        cursorCipherGraphqlNodeService = cursorCipherGraphqlNodeService
      )

    val connection =
      connectionBuilder.build(
        clazz = CursorProxyTestBaseEntity::class.java,
        query = query,
        first = 1,
        last = null,
        after = null,
        before = null,
        order = null,
        builder =
          object : GraphqlRelayResponseObjectBuilder<CursorProxyTestBaseEntity, CursorProxyTestNode> {
            override fun get(from: CursorProxyTestBaseEntity): CursorProxyTestNode {
              val entity = Hibernate.unproxy(from) as CursorProxyTestEntity

              return CursorProxyTestNode("node-${entity.getId()}")
            }
          }
      )

    val edge = connection.edges.single()
    val cursorContainer =
      cursorCipherGraphqlNodeService.decryptFromCursor(CursorContainer::class.java, edge.cursor)

    assertEquals(500, cursorContainer.nodeId)
    assertEquals(listOf("id"), cursorContainer.cursorFields)
    assertEquals(listOf("7"), cursorContainer.cursorValues)

    val pageCaptor =
      ArgumentCaptor.forClass(KeyedPage::class.java) as ArgumentCaptor<KeyedPage<CursorProxyTestBaseEntity>>
    Mockito.verify(selectionQuery).getKeyedResultList(pageCaptor.capture())

    val defaultOrder = pageCaptor.value.keyDefinition.single()
    assertEquals("id", defaultOrder.attributeName())
    assertEquals(SortDirection.DESCENDING, defaultOrder.direction())
  }

  @Suppress("UNCHECKED_CAST")
  @Test
  fun `build creates custom order cursor from unproxied implementation when base proxy is not concrete entity instance`() {
    val proxy = hibernateProxyEntity(id = 7, name = "proxy-name")

    val cursorCipherGraphqlNodeService =
      CursorCipherGraphqlNodeService(IdentityGraphqlRelayDeterministicCipher)

    val graphqlRelayNodeManager =
      GraphqlRelayNodeManager(NodeIdCipherGraphqlNodeService(IdentityGraphqlRelayDeterministicCipher))

    val selectionQuery =
      Mockito.mock(SelectionQuery::class.java) as SelectionQuery<CursorProxyTestBaseEntity>
    val resultPage =
      Page
        .first(1)
        .keyedBy(Order.asc(CursorProxyTestBaseEntity::class.java, "name"))
    val keyedResultList =
      KeyedResultList(
        listOf(proxy),
        listOf(listOf("proxy-name", 7L)),
        resultPage,
        null,
        null
      )

    Mockito
      .`when`(selectionQuery.getKeyedResultList(ArgumentMatchers.any<KeyedPage<CursorProxyTestBaseEntity>>()))
      .thenReturn(keyedResultList)

    val entityManager = Mockito.mock(EntityManager::class.java)
    val session = Mockito.mock(Session::class.java)
    val query = Mockito.mock(CriteriaQuery::class.java) as CriteriaQuery<CursorProxyTestBaseEntity>

    Mockito
      .`when`(entityManager.unwrap(Session::class.java))
      .thenReturn(session)
    Mockito
      .`when`(session.createSelectionQuery(query))
      .thenReturn(selectionQuery)

    val connectionBuilder =
      ConnectionGQLObjectFromQueryBuilder(
        entityManager = entityManager,
        graphqlRelayNodeManager = graphqlRelayNodeManager,
        cursorCipherGraphqlNodeService = cursorCipherGraphqlNodeService
      )

    val connection =
      connectionBuilder.build(
        clazz = CursorProxyTestBaseEntity::class.java,
        query = query,
        first = 1,
        last = null,
        after = null,
        before = null,
        order = listOf(CursorProxyTestOrderInput(name = OrderTypeGQLEnum.ASC)),
        builder =
          object : GraphqlRelayResponseObjectBuilder<CursorProxyTestBaseEntity, CursorProxyTestNode> {
            override fun get(from: CursorProxyTestBaseEntity): CursorProxyTestNode {
              val entity = Hibernate.unproxy(from) as CursorProxyTestEntity

              return CursorProxyTestNode("node-${entity.getId()}")
            }
          }
      )

    val edge = connection.edges.single()
    val cursorContainer =
      cursorCipherGraphqlNodeService.decryptFromCursor(CursorContainer::class.java, edge.cursor)

    assertEquals(500, cursorContainer.nodeId)
    assertEquals(listOf("name", "id"), cursorContainer.cursorFields)
    assertEquals(listOf("proxy-name", "7"), cursorContainer.cursorValues)
  }

  private fun nodeInfo(
    nodeId: Long,
    entityClass: Class<*>,
    objectIdGetter: (Any) -> Any = { 1L }
  ): GraphqlRelayNodeManager.NodeInfo {
    return GraphqlRelayNodeManager.NodeInfo(
      nodeId = nodeId,
      entityClass = entityClass,
      graphqlObjectClass = DummyGraphqlNode::class.java,
      graphqlType = "DummyGraphqlNode",
      objectIdGetter = objectIdGetter,
      entityGetter =
        object : GraphqlRelayEntityGetterFactoryService.GraphqlRelayEntityGetter {
          override fun getEntityById(id: Long): Any? = null
        },
      graphqlNodeGetter = { null }
    )
  }

  private abstract class SubjectDomainEntity

  private class IndividualDomainEntity : SubjectDomainEntity()

  private class LegalDomainEntity : SubjectDomainEntity()

  private class UnrelatedDomainEntity

  private open class CursorProxyTestBaseEntity

  private class CursorProxyTestBaseEntityHibernateProxy(
    private val lazyInitializer: LazyInitializer
  ) : CursorProxyTestBaseEntity(), HibernateProxy {
    override fun getHibernateLazyInitializer(): LazyInitializer = lazyInitializer

    override fun writeReplace(): Any = this
  }

  private fun hibernateProxyEntity(
    id: Long = 7,
    name: String = "proxy-name"
  ): CursorProxyTestBaseEntityHibernateProxy {
    val implementation = CursorProxyTestEntity(id, name)
    val lazyInitializer = Mockito.mock(LazyInitializer::class.java)

    Mockito
      .`when`(lazyInitializer.implementation)
      .thenReturn(implementation)

    return CursorProxyTestBaseEntityHibernateProxy(
      lazyInitializer = lazyInitializer
    )
  }

  private class CursorProxyTestOrderInput(
    @get:GraphqlConnectionOrderField(
      fieldType = GraphqlConnectionOrderField.Type.STRING,
      entityFieldName = "name",
      entityValueGetter = "getName"
    )
    var name: OrderTypeGQLEnum?
  )

  private object IdentityGraphqlRelayDeterministicCipher : GraphqlRelayDeterministicCipher {
    override fun encrypt(input: ByteArray): ByteArray = input

    override fun decrypt(input: ByteArray): ByteArray = input
  }

  private data class DummyGraphqlNode(
    override val id: String = "dummy"
  ) : GraphqlNode
}
