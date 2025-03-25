package ru.code4a.graphql.relay.interfaces

/**
 * Interface for a factory service that creates entity getters.
 * This service is loaded via Java ServiceLoader to enable customized entity retrieval strategies.
 */
interface GraphqlRelayEntityGetterFactoryService {

  /**
   * Creates an entity getter for a specific GraphQL node class and entity class pair.
   *
   * @param graphqlRelayNodeEntityObjectClass The GraphQL node class
   * @param entityClass The entity class
   * @return A getter that can retrieve entities by ID
   */
  fun createGetter(graphqlRelayNodeEntityObjectClass: Class<*>, entityClass: Class<*>): GraphqlRelayEntityGetter

  /**
   * Interface for retrieving entity objects by ID.
   * Implementations provide secure access to entity objects,
   * potentially implementing access control and other security measures.
   */
  interface GraphqlRelayEntityGetter {
    /**
     * Retrieves an entity by its ID.
     * Implementations should perform access control checks before returning entities.
     *
     * @param id The entity ID
     * @return The entity object, or null if not found or inaccessible
     */
    fun getEntityById(id: Long): Any?
  }
}
