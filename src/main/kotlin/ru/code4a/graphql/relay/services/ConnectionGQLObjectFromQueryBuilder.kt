package ru.code4a.graphql.relay.services

import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.CriteriaQuery
import org.hibernate.Session
import org.hibernate.query.Order
import org.hibernate.query.Page
import ru.code4a.graphql.relay.annotations.GraphqlConnectionOrderField
import ru.code4a.graphql.relay.annotations.GraphqlReadDatabaseMethod
import ru.code4a.graphql.relay.exceptions.CannotBeTakenWithThisLimitGraphqlException
import ru.code4a.graphql.relay.exceptions.InvalidCombinationUsageCursorPaginationParamsGraphqlException
import ru.code4a.graphql.relay.interfaces.GraphqlRelayResponseObjectBuilder
import ru.code4a.graphql.relay.schema.enums.OrderTypeGQLEnum
import ru.code4a.graphql.relay.schema.inputs.OrderIdOnlyGQLInput
import ru.code4a.graphql.relay.schema.objects.ConnectionGQLObject
import ru.code4a.graphql.relay.services.containers.CursorContainer
import java.time.Instant

/**
 * Service for building ConnectionGQLObject instances from JPA queries.
 * Implements cursor-based pagination with ordering support for efficient database access.
 *
 * @property entityManager JPA entity manager for database operations
 * @property graphqlRelayNodeManager Manager for node operations
 * @property cursorCipherGraphqlNodeService Service for cursor encryption/decryption
 */
@ApplicationScoped
class ConnectionGQLObjectFromQueryBuilder(
  val entityManager: EntityManager,
  val graphqlRelayNodeManager: GraphqlRelayNodeManager,
  val cursorCipherGraphqlNodeService: CursorCipherGraphqlNodeService
) {
  /**
   * Internal class for mapping between GraphQL order inputs and entity fields.
   *
   * @property entityFieldName Name of the entity field for ordering
   * @property entityValueGetter Method name to get the field value
   * @property converterToString Function to convert field value to string
   * @property converterFromString Function to convert string to field value
   * @property orderType Direction of ordering (ASC/DESC)
   */
  private class OrderConverter(
    val entityFieldName: String,
    val entityValueGetter: String,
    val converterToString: (value: Comparable<*>) -> String,
    val converterFromString: (value: String) -> Comparable<*>,
    val orderType: OrderTypeGQLEnum
  )

  /**
   * Helper class for cursor operations, handling conversion between
   * database values and cursor strings.
   *
   * @property orderConverters Array of field converters for ordering
   * @property graphqlRelayNodeManager Manager for node operations
   * @property cursorCipherGraphqlNodeService Service for cursor encryption/decryption
   */
  private class CursorConverter(
    val orderConverters: Array<OrderConverter>,
    val graphqlRelayNodeManager: GraphqlRelayNodeManager,
    val cursorCipherGraphqlNodeService: CursorCipherGraphqlNodeService
  ) {
    fun <T> getHibernateOrders(clazz: Class<T>): List<Order<T>> =
      orderConverters.map { orderConverter ->
        when (orderConverter.orderType) {
          OrderTypeGQLEnum.ASC ->
            Order.asc(clazz, orderConverter.entityFieldName).reverse()

          OrderTypeGQLEnum.DESC ->
            Order.desc(clazz, orderConverter.entityFieldName)
        }
      }

    fun <T> getHibernateOrdersReversed(clazz: Class<T>): List<Order<T>> =
      orderConverters.map { orderConverter ->
        when (orderConverter.orderType) {
          OrderTypeGQLEnum.DESC ->
            Order.asc(clazz, orderConverter.entityFieldName)

          OrderTypeGQLEnum.ASC ->
            Order.desc(clazz, orderConverter.entityFieldName)
        }
      }

    fun convertCursorToValues(
      entityClass: Class<*>,
      currentCursorConverter: CursorConverter,
      cursor: String
    ): List<Comparable<*>> {
      val graphqlNodeInfo = graphqlRelayNodeManager.getNodeInfoByEntityClass(entityClass)

      require(graphqlNodeInfo != null) { "Entity $entityClass must have a node info" }

      val cursorContainer =
        cursorCipherGraphqlNodeService
          .decryptFromCursor(CursorContainer::class.java, cursor)

      require(cursorContainer.nodeId == graphqlNodeInfo.nodeId) {
        "Entity $entityClass must be equal to ${cursorContainer.nodeId}"
      }

      val currentUsedOrderFieldsSet =
        currentCursorConverter
          .orderConverters
          .map { orderConverter ->
            orderConverter.entityFieldName
          }
          .toSet()

      for (cursorField in cursorContainer.cursorFields) {
        require(cursorField in currentUsedOrderFieldsSet) {
          "Cursor field ($cursorField) in cursor must be used order object. Current used orders: $currentUsedOrderFieldsSet"
        }
      }

      return cursorContainer
        .cursorValues
        .mapIndexed { i, value ->
          orderConverters[i].converterFromString(value)
        }
    }

    fun createCursorFromEntity(entity: Any): String {
      val graphqlNodeInfo = graphqlRelayNodeManager.getNodeInfoByEntityClass(entity::class.java)

      require(graphqlNodeInfo != null) { "Entity ${entity::class.java} must have a node info" }

      val cursorContainer =
        CursorContainer(
          nodeId = graphqlNodeInfo.nodeId,
          cursorFields = orderConverters
            .map { orderConverter ->
              orderConverter.entityFieldName
            },
          cursorValues = orderConverters
            .map { orderConverter ->
              val value =
                entity
                  .javaClass
                  .getMethod(
                    orderConverter.entityValueGetter
                  )
                  .invoke(
                    entity
                  )

              require(value != null)

              orderConverter.converterToString(
                value as Comparable<*>
              )
            }
        )

      return cursorCipherGraphqlNodeService.encryptToCursor(cursorContainer)
    }
  }

  private fun buildConverter(orders: List<Any>): CursorConverter {
    val orderConverters = mutableListOf<OrderConverter>()
    val addedFields = mutableSetOf<String>()

    for (order in orders) {
      for (method in order.javaClass.methods) {
        val graphqlOrderAnnotation = method.getAnnotation(GraphqlConnectionOrderField::class.java)

        if (graphqlOrderAnnotation != null) {
          if (graphqlOrderAnnotation.entityFieldName in addedFields) {
            continue
          }

          val value = method.invoke(order)

          if (value != null) {
            val orderType = value as OrderTypeGQLEnum

            val converterToString =
              when (graphqlOrderAnnotation.fieldType) {
                GraphqlConnectionOrderField.Type.STRING -> {
                  { value: Comparable<*> ->
                    value as String
                  }
                }

                GraphqlConnectionOrderField.Type.LONG -> {
                  { value: Comparable<*> ->
                    (value as Long).toString()
                  }
                }

                GraphqlConnectionOrderField.Type.INSTANT -> {
                  { value: Comparable<*> ->
                    (value as Instant).toString()
                  }
                }
              }

            val converterFromString =
              when (graphqlOrderAnnotation.fieldType) {
                GraphqlConnectionOrderField.Type.STRING -> {
                  { value: String ->
                    value
                  }
                }

                GraphqlConnectionOrderField.Type.LONG -> {
                  { value: String ->
                    value.toLong()
                  }
                }

                GraphqlConnectionOrderField.Type.INSTANT -> {
                  { value: String ->
                    Instant.parse(value)
                  }
                }
              }

            addedFields.add(graphqlOrderAnnotation.entityFieldName)

            orderConverters.add(
              OrderConverter(
                entityFieldName = graphqlOrderAnnotation.entityFieldName,
                converterToString = converterToString,
                converterFromString = converterFromString,
                orderType = orderType,
                entityValueGetter = graphqlOrderAnnotation.entityValueGetter
              )
            )
          }
        }
      }
    }

    return CursorConverter(
      orderConverters = orderConverters.toTypedArray(),
      graphqlRelayNodeManager = graphqlRelayNodeManager,
      cursorCipherGraphqlNodeService = cursorCipherGraphqlNodeService
    )
  }

  /**
   * Builds a Connection object from a JPA criteria query with pagination and ordering.
   * Implements the Relay Connection specification with cursor-based pagination.
   *
   * @param T Entity type for the query
   * @param R GraphQL node type for the connection
   * @param clazz Class object for entity type T
   * @param query JPA criteria query to execute
   * @param first Number of items to fetch (forward pagination)
   * @param last Number of items to fetch (backward pagination)
   * @param after Cursor to fetch items after (forward pagination)
   * @param before Cursor to fetch items before (backward pagination)
   * @param order List of ordering criteria
   * @param builder Converter from entity objects to GraphQL node objects
   * @return A Connection object with edges and pagination info
   * @throws InvalidCombinationUsageCursorPaginationParamsGraphqlException if incompatible pagination parameters are used
   * @throws CannotBeTakenWithThisLimitGraphqlException if the pagination limit exceeds the maximum
   */
  @GraphqlReadDatabaseMethod
  fun <T : Any, R : Any> build(
    clazz: Class<T>,
    query: CriteriaQuery<T>,
    first: Int?,
    last: Int?,
    after: String?,
    before: String?,
    order: List<Any>?,
    builder: GraphqlRelayResponseObjectBuilder<T, R>,
    limit: Int = 100
  ): ConnectionGQLObject<R> {
    if (first != null && last != null) {
      throw InvalidCombinationUsageCursorPaginationParamsGraphqlException(listOf("first", "last"))
    }
    if (after != null && before != null) {
      throw InvalidCombinationUsageCursorPaginationParamsGraphqlException(listOf("after", "before"))
    }
    if (first != null && before != null) {
      throw InvalidCombinationUsageCursorPaginationParamsGraphqlException(listOf("first", "before"))
    }
    if (last != null && after != null) {
      throw InvalidCombinationUsageCursorPaginationParamsGraphqlException(listOf("last", "after"))
    }

    val isForwardPagination = first != null || after != null
    val isBackwardPagination = last != null || before != null

    val cursorConverter =
      if (order == null) {
        buildConverter(listOf(OrderIdOnlyGQLInput(id = OrderTypeGQLEnum.DESC)))
      } else {
        buildConverter(order + listOf(OrderIdOnlyGQLInput(id = OrderTypeGQLEnum.DESC)))
      }

    val numberRowsToBeFetched =
      when {
        first != null -> {
          if (first > limit) {
            throw CannotBeTakenWithThisLimitGraphqlException(first.toLong())
          }

          first
        }

        last != null -> {
          if (last > limit) {
            throw CannotBeTakenWithThisLimitGraphqlException(last.toLong())
          }

          last
        }

        else -> limit
      }

    val queryPage = Page.first(numberRowsToBeFetched)

    val queryKeyedPage =
      when {
        isForwardPagination -> queryPage.keyedBy(cursorConverter.getHibernateOrders(clazz))
        isBackwardPagination -> queryPage.keyedBy(cursorConverter.getHibernateOrdersReversed(clazz))
        else -> queryPage.keyedBy(cursorConverter.getHibernateOrders(clazz))
      }

    val queryCurrentKeyedPage =
      when {
        after != null -> queryKeyedPage.nextPage(
          cursorConverter.convertCursorToValues(
            entityClass = clazz,
            cursor = after,
            currentCursorConverter = cursorConverter
          ).toMutableList()
        )

        before != null -> queryKeyedPage.nextPage(
          cursorConverter.convertCursorToValues(
            entityClass = clazz,
            cursor = before,
            currentCursorConverter = cursorConverter
          ).toMutableList()
        )

        else -> queryKeyedPage
      }

    val selectionQuery = entityManager.unwrap(Session::class.java).createSelectionQuery(query)

    val keyedResultList = selectionQuery.getKeyedResultList(queryCurrentKeyedPage)

    val resultList =
      if (isBackwardPagination) {
        keyedResultList.resultList.reversed()
      } else {
        keyedResultList.resultList
      }

    val hasNextPage =
      when {
        isForwardPagination -> !keyedResultList.isLastPage
        isBackwardPagination -> !keyedResultList.isFirstPage
        else -> !keyedResultList.isLastPage
      }

    val hasPreviousPage =
      when {
        isBackwardPagination -> !keyedResultList.isLastPage
        isForwardPagination -> !keyedResultList.isFirstPage
        else -> !keyedResultList.isFirstPage
      }

    val edges =
      resultList.map { entity ->
        ConnectionGQLObject.Edge(
          cursor = cursorConverter.createCursorFromEntity(entity),
          node = builder.get(entity)
        )
      }

    return ConnectionGQLObject(
      edges = edges,
      pageInfo =
        ConnectionGQLObject.PageInfo(
          hasPreviousPage = hasPreviousPage,
          hasNextPage = hasNextPage,
          startCursorBuilder = {
            edges.firstOrNull()?.cursor
          },
          endCursorBuilder = {
            edges.lastOrNull()?.cursor
          }
        )
    )
  }
}

/**
 * Extension function to build a Connection object with type inference.
 * Simplifies the API by using Kotlin's reified type parameters.
 *
 * @param T Entity type for the query
 * @param R GraphQL node type for the connection
 * @param query JPA criteria query to execute
 * @param first Number of items to fetch (forward pagination)
 * @param last Number of items to fetch (backward pagination)
 * @param after Cursor to fetch items after (forward pagination)
 * @param before Cursor to fetch items before (backward pagination)
 * @param order List of ordering criteria
 * @param builder Converter from entity objects to GraphQL node objects
 * @return A Connection object with edges and pagination info
 */
inline fun <reified T : Any, R : Any> ConnectionGQLObjectFromQueryBuilder.build(
  query: CriteriaQuery<T>,
  first: Int?,
  last: Int?,
  after: String?,
  before: String?,
  order: List<Any>?,
  builder: GraphqlRelayResponseObjectBuilder<T, R>,
  limit: Int = 100
): ConnectionGQLObject<R> =
  this.build(
    clazz = T::class.java,
    query = query,
    first = first,
    last = last,
    after = after,
    before = before,
    order = order,
    builder = builder,
    limit = limit
  )
