package ru.code4a.graphql.relay.services

import org.eclipse.microprofile.graphql.Name
import ru.code4a.graphql.relay.annotations.GraphqlRelayNodeEntityById
import ru.code4a.graphql.relay.interfaces.GraphqlRelayEntityGQLObjectBuilderGlobalService
import ru.code4a.graphql.relay.interfaces.GraphqlRelayEntityGetterFactoryService
import ru.code4a.graphql.relay.schema.objects.GraphqlNode

open class CursorProxyTestEntity(
  private val id: Long,
  private val name: String
) {
  open fun getId(): Long = id

  open fun getName(): String = name
}

@Name("CursorProxyTestNode")
@GraphqlRelayNodeEntityById(nodeId = 500)
data class CursorProxyTestNode(
  override val id: String = "cursor-proxy-test-node"
) : GraphqlNode

class CursorProxyTestObjectBuilderGlobalService : GraphqlRelayEntityGQLObjectBuilderGlobalService {
  override fun create(graphqlRelayNodeEntityObjectClass: Class<*>):
    GraphqlRelayEntityGQLObjectBuilderGlobalService.GraphqlRelayEntityGQLObjectBuilderItem {
    require(graphqlRelayNodeEntityObjectClass == CursorProxyTestNode::class.java)

    return object : GraphqlRelayEntityGQLObjectBuilderGlobalService.GraphqlRelayEntityGQLObjectBuilderItem {
      override val entityClass: Class<*> = CursorProxyTestEntity::class.java

      override fun get(entity: Any): Any {
        val cursorProxyTestEntity = entity as CursorProxyTestEntity

        return CursorProxyTestNode("node-${cursorProxyTestEntity.getId()}")
      }
    }
  }
}

class CursorProxyTestEntityGetterFactoryService : GraphqlRelayEntityGetterFactoryService {
  override fun createGetter(
    graphqlRelayNodeEntityObjectClass: Class<*>,
    entityClass: Class<*>
  ): GraphqlRelayEntityGetterFactoryService.GraphqlRelayEntityGetter {
    require(graphqlRelayNodeEntityObjectClass == CursorProxyTestNode::class.java)
    require(entityClass == CursorProxyTestEntity::class.java)

    return object : GraphqlRelayEntityGetterFactoryService.GraphqlRelayEntityGetter {
      override fun getEntityById(id: Long): Any? = null
    }
  }
}
