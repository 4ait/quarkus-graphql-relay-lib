package ru.code4a.graphql.relay.annotations

/**
 * Annotation for fields in GraphQL input objects that define ordering criteria for connections.
 * Specifies how a field in the input object maps to an entity field for sorting.
 *
 * @param fieldType The data type of the field (LONG or STRING)
 * @param entityFieldName The name of the corresponding field in the entity
 * @param entityValueGetter The name of the getter method to retrieve the field value from the entity
 */
annotation class GraphqlConnectionOrderField(
  val fieldType: Type,
  val entityFieldName: String,
  val entityValueGetter: String
) {
  enum class Type {
    LONG,
    STRING
  }
}
