package ru.code4a.graphql.relay.services

import kotlin.test.Test
import kotlin.test.assertFailsWith
import ru.code4a.graphql.relay.interfaces.GraphqlRelayEntityGetterFactoryService
import ru.code4a.graphql.relay.schema.objects.GraphqlNode

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

  private fun nodeInfo(
    nodeId: Long,
    entityClass: Class<*>
  ): GraphqlRelayNodeManager.NodeInfo {
    return GraphqlRelayNodeManager.NodeInfo(
      nodeId = nodeId,
      entityClass = entityClass,
      graphqlObjectClass = DummyGraphqlNode::class.java,
      graphqlType = "DummyGraphqlNode",
      objectIdGetter = { 1L },
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

  private data class DummyGraphqlNode(
    override val id: String = "dummy"
  ) : GraphqlNode
}
