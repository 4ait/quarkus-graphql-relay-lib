package ru.code4a.graphql.relay.schema.inputs

import org.eclipse.microprofile.graphql.Input
import ru.code4a.graphql.relay.annotations.GraphqlConnectionOrderField
import ru.code4a.graphql.relay.schema.enums.OrderTypeGQLEnum

/**
 * Default GraphQL input type for ordering by ID.
 * Provides a standard way to order connections by the entity's ID field.
 */
@Input("OrderIdOnly")
class OrderIdOnlyGQLInput(
  /**
   * The sort direction for the ID field.
   * If null, this ordering will not be applied.
   */
  @get:GraphqlConnectionOrderField(
    fieldType = GraphqlConnectionOrderField.Type.LONG,
    entityFieldName = "id",
    entityValueGetter = "getId"
  )
  var id: OrderTypeGQLEnum?
)
