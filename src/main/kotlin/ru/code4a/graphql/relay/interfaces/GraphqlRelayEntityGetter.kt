package ru.code4a.graphql.relay.interfaces

/**
 * Interface for retrieving entity objects by ID.
 * Implementations provide secure access to entity objects,
 * potentially implementing access control and other security measures.
 */
interface GraphqlRelayEntityGetter {
  /**
   * Retrieves an entity by its ID for a specific GraphQL node class.
   * Implementations should perform access control checks before returning entities.
   *
   * @param graphqlRelayNodeEntityObjectClass The GraphQL node class
   * @param id The entity ID
   * @return The entity object, or null if not found or inaccessible
   */
  fun getEntityById(graphqlRelayNodeEntityObjectClass: Class<*>, id: Long): Any?
}
