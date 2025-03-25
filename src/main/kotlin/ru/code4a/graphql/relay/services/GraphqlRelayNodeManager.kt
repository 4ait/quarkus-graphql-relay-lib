package ru.code4a.graphql.relay.services

import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import org.eclipse.microprofile.graphql.Name
import ru.code4a.graphql.relay.annotations.GraphqlReadDatabaseMethod
import ru.code4a.graphql.relay.annotations.GraphqlRelayNodeEntityById
import ru.code4a.graphql.relay.interfaces.GraphqlRelayEntityGQLObjectBuilderGlobalService
import ru.code4a.graphql.relay.interfaces.GraphqlRelayEntityGetter
import ru.code4a.graphql.relay.schema.objects.GraphqlNode
import ru.code4a.graphql.relay.services.containers.GraphqlNodeEntityContainerID
import ru.code4a.graphql.relay.utils.bytes.asLong
import java.security.MessageDigest
import java.util.*
import kotlin.reflect.full.superclasses

/**
 * Central manager for GraphQL Relay node operations.
 * Handles the mapping between entity objects and their GraphQL node representations,
 * node identification, and resolution of globally unique IDs.
 *
 * This class is responsible for:
 * - Mapping between entity classes and their GraphQL node representations
 * - Generating and resolving globally unique IDs for nodes
 * - Providing type-safe access to entity and node data
 * - Managing node security through encrypted IDs
 *
 * @property nodeIdCipherGraphqlNodeService Service for encrypting and decrypting node IDs
 * @property entityManager JPA entity manager for database operations
 * @property graphqlRelayEntityGetter Service for retrieving entity objects
 */
@ApplicationScoped
class GraphqlRelayNodeManager(
  private val nodeIdCipherGraphqlNodeService: NodeIdCipherGraphqlNodeService,
  private val entityManager: EntityManager,
  private val graphqlRelayEntityGetter: GraphqlRelayEntityGetter
) {

  /**
   * Contains information about a registered node type.
   *
   * @property nodeId The unique numeric ID for this node type
   * @property entityClass The JPA entity class for this node
   * @property graphqlObjectClass The GraphQL object class for this node
   * @property graphqlType The GraphQL type name for this node
   * @property objectIdGetter Function to extract the entity ID from an entity instance
   * @property entityGetterById Function to retrieve an entity by its ID
   * @property graphqlNodeGetter Function to retrieve a GraphQL node by its entity ID
   */
  class NodeInfo(
    val nodeId: Long,
    val entityClass: Class<*>,
    val graphqlObjectClass: Class<*>,
    val graphqlType: String,
    val objectIdGetter: (Any) -> Any,
    val entityGetterById: (Long, GraphqlRelayEntityGetter) -> Any?,
    val graphqlNodeGetter: (Long, GraphqlRelayEntityGetter) -> GraphqlNode?
  )

  companion object {
    private val nodeInfoByNodeId: Map<Long, NodeInfo>
    private val nodeInfoByObjectGraphqlType: Map<String, NodeInfo>
    private val nodeInfoByObjectGraphqlClass: Map<Class<*>, NodeInfo>
    private val nodeInfoByEntityClass: Map<Class<*>, NodeInfo>

    init {
      val nodeInfoByNodeId = mutableMapOf<Long, NodeInfo>()
      val nodeInfoByObjectGraphqlType = mutableMapOf<String, NodeInfo>()
      val nodeInfoByObjectGraphqlClass = mutableMapOf<Class<*>, NodeInfo>()
      val nodeInfoByEntityClass = mutableMapOf<Class<*>, NodeInfo>()

      val graphqlRelayNodeEntityObjectsClassNames: List<String> =
        GraphqlRelayNodeManager::class
          .java
          .getResource("ru/code4a/graphql/relay/gen/relaynodeobjects")!!
          .readText()
          .split("\n")

      val graphqlRelayEntityGQLObjectBuilderGlobalService =
        ServiceLoader.load(GraphqlRelayEntityGQLObjectBuilderGlobalService::class.java).first()

      val md5 = MessageDigest.getInstance("MD5")

      for (graphqlRelayNodeEntityObjectClassName in graphqlRelayNodeEntityObjectsClassNames) {
        val graphqlRelayNodeEntityObjectClass = Class.forName(graphqlRelayNodeEntityObjectClassName)

        if (!graphqlRelayNodeEntityObjectClass.kotlin.superclasses.contains(GraphqlNode::class)) {
          throw RuntimeException(
            "Class ${graphqlRelayNodeEntityObjectClass.name} " +
              "must be inherited from GraphqlRelayNode"
          )
        }

        val entityObjectBuilder =
          graphqlRelayEntityGQLObjectBuilderGlobalService.create(graphqlRelayNodeEntityObjectClass)

        val entityClass = entityObjectBuilder.entityClass

        val annotationConfig =
          graphqlRelayNodeEntityObjectClass.getDeclaredAnnotation(GraphqlRelayNodeEntityById::class.java).let {
            it ?: GraphqlRelayNodeEntityById()
          }

        val objectGraphqlType =
          graphqlRelayNodeEntityObjectClass.getDeclaredAnnotation(Name::class.java).value

        val nodeId =
          if (annotationConfig.nodeId == Long.MIN_VALUE) {
            md5.digest((objectGraphqlType + "NJsdf44ffsdfsdc*fcff").toByteArray()).copyOfRange(0, 8).asLong()
          } else {
            annotationConfig.nodeId
          }

        val nodeInfoWithCurrentNodeId = nodeInfoByNodeId[nodeId]

        if (nodeInfoWithCurrentNodeId != null) {
          throw RuntimeException(
            "Found duplicated node id $nodeId. " +
              "${nodeInfoWithCurrentNodeId.graphqlObjectClass.name} - ${graphqlRelayNodeEntityObjectClass.name}"
          )
        }

        val nodeInfoWithCurrentObjectGraphqlType = nodeInfoByObjectGraphqlType[objectGraphqlType]

        if (nodeInfoWithCurrentObjectGraphqlType != null) {
          throw RuntimeException(
            "Found duplicated object graphql type $objectGraphqlType. " +
              "${nodeInfoWithCurrentObjectGraphqlType.graphqlObjectClass.name} - " +
              graphqlRelayNodeEntityObjectClass.name
          )
        }

        val entityGetIdMethod = entityClass.declaredMethods.find { method -> method.name == "getId" }

        require(entityGetIdMethod != null) { "Cannot find getId for entity class ${entityClass.name}" }

        val entityGetterById =
          { id: Long, graphqlRelayEntityGetter: GraphqlRelayEntityGetter ->
            graphqlRelayEntityGetter.getEntityById(graphqlRelayNodeEntityObjectClass, id)
          }

        val objectIdGetter =
          { obj: Any ->
            entityGetIdMethod.invoke(obj)
          }

        val graphqlNodeGetter =
          { id: Long, graphqlRelayEntityGetter: GraphqlRelayEntityGetter ->
            val entity = entityGetterById(id, graphqlRelayEntityGetter)

            if (entity == null) {
              null
            } else {
              entityObjectBuilder.get(entity) as GraphqlNode
            }
          }

        val nodeInfo =
          NodeInfo(
            nodeId = nodeId,
            entityClass = entityClass,
            graphqlObjectClass = graphqlRelayNodeEntityObjectClass,
            graphqlType = objectGraphqlType,
            objectIdGetter = objectIdGetter,
            entityGetterById = entityGetterById,
            graphqlNodeGetter = graphqlNodeGetter
          )

        nodeInfoByNodeId[nodeId] = nodeInfo
        nodeInfoByObjectGraphqlType[objectGraphqlType] = nodeInfo
        nodeInfoByObjectGraphqlClass[graphqlRelayNodeEntityObjectClass] = nodeInfo
        nodeInfoByEntityClass[entityClass] = nodeInfo
      }

      this.nodeInfoByNodeId = nodeInfoByNodeId
      this.nodeInfoByObjectGraphqlType = nodeInfoByObjectGraphqlType
      this.nodeInfoByObjectGraphqlClass = nodeInfoByObjectGraphqlClass
      this.nodeInfoByEntityClass = nodeInfoByEntityClass
    }
  }

  /**
   * Gets node information for the specified entity class.
   *
   * @param entityClass The entity class to look up
   * @return NodeInfo for the entity class, or null if not found
   */
  fun getNodeInfoByEntityClass(entityClass: Class<*>): NodeInfo? {
    return nodeInfoByEntityClass[entityClass]
  }

  /**
   * Generates a node ID for an entity object with the specified GraphQL type.
   *
   * @param obj The entity object
   * @param graphqlType The GraphQL type name
   * @return A globally unique ID string for the object
   * @throws IllegalArgumentException if the GraphQL type is not registered
   */
  fun getNodeIdForObject(obj: Any, graphqlType: String): String {
    val nodeInfo = nodeInfoByObjectGraphqlType[graphqlType]

    require(nodeInfo != null) { "GraphqlType $graphqlType is not found" }

    return nodeInfo.buildID(obj)
  }

  /**
   * Generates a node ID for an entity object using its corresponding GraphQL node object.
   *
   * @param obj The entity object
   * @param nodeObject The GraphQL node object
   * @return A globally unique ID string for the object
   * @throws IllegalArgumentException if the node object type is not registered
   */
  fun <T : GraphqlNode> getNodeIdForObject(obj: Any, nodeObject: T): String {
    val nodeInfo = nodeInfoByObjectGraphqlClass[nodeObject::class.java]

    require(nodeInfo != null) { "Node with object class ${nodeObject::class} is not found" }

    return nodeInfo.buildID(obj)
  }

  /**
   * Resolves an entity object from a globally unique node ID.
   *
   * @param nodeID The globally unique ID to resolve
   * @return The entity object, or null if not found or inaccessible
   * @throws IllegalArgumentException if the node ID is invalid or references an unknown node type
   */
  @GraphqlReadDatabaseMethod
  fun getEntityByNodeId(nodeID: String): Any? {
    val graphqlNodeEntityContainerID =
      nodeIdCipherGraphqlNodeService.decryptFromNodeId(GraphqlNodeEntityContainerID::class.java, nodeID)

    val nodeInfo = nodeInfoByNodeId[graphqlNodeEntityContainerID.nodeId]

    require(nodeInfo != null) { "Node with object id $nodeID not found" }

    return nodeInfo.entityGetterById(graphqlNodeEntityContainerID.entityId, graphqlRelayEntityGetter)
  }

  /**
   * Resolves a GraphQL node object from a globally unique node ID.
   *
   * @param nodeID The globally unique ID to resolve
   * @return The GraphQL node object, or null if not found or inaccessible
   * @throws IllegalArgumentException if the node ID is invalid or references an unknown node type
   */
  @GraphqlReadDatabaseMethod
  fun getGraphqlNodeByNodeId(nodeID: String): GraphqlNode? {
    val graphqlNodeEntityContainerID =
      nodeIdCipherGraphqlNodeService.decryptFromNodeId(GraphqlNodeEntityContainerID::class.java, nodeID)

    val nodeInfo = nodeInfoByNodeId[graphqlNodeEntityContainerID.nodeId]

    require(nodeInfo != null) { "Node with object id $nodeID not found" }

    return nodeInfo.graphqlNodeGetter(graphqlNodeEntityContainerID.entityId, graphqlRelayEntityGetter)
  }

  private fun NodeInfo.buildID(obj: Any): String {
    val entityId = objectIdGetter.invoke(obj) as Long

    return nodeIdCipherGraphqlNodeService.encryptToNodeId(
      GraphqlNodeEntityContainerID(
        nodeId = nodeId,
        entityId = entityId
      )
    )
  }
}


@GraphqlReadDatabaseMethod
fun <T> GraphqlRelayNodeManager.getTypedEntityByNodeId(nodeID: String): T? {
  return getEntityByNodeId(nodeID) as? T?
}
