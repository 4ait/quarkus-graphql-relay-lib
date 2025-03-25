package ru.code4a.graphql.relay.interfaces

/**
 * Interface for a global service that creates builders for GraphQL entity objects.
 * This service is loaded via Java ServiceLoader to enable build-time processing
 * of GraphQL node types.
 */
interface GraphqlRelayEntityGQLObjectBuilderGlobalService {
  /**
   * Creates a builder for the specified GraphQL node class.
   *
   * @param graphqlRelayNodeEntityObjectClass The GraphQL node class
   * @return A builder that can convert between entity and GraphQL objects
   */
  fun create(graphqlRelayNodeEntityObjectClass: Class<*>): GraphqlRelayEntityGQLObjectBuilderItem

  /**
   * Interface for a builder that can convert between entity and GraphQL objects.
   */
  interface GraphqlRelayEntityGQLObjectBuilderItem {
    /**
     * The entity class that this builder works with.
     */
    val entityClass: Class<*>

    /**
     * Creates a GraphQL object from an entity object.
     *
     * @param entity The entity object to convert
     * @return The corresponding GraphQL object
     */
    fun get(entity: Any): Any
  }
}
