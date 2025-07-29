package ru.code4a.graphql.relay.utils

import jakarta.persistence.criteria.Nulls
import org.hibernate.query.Order


fun Nulls.reverse(): Nulls {
  return when (this) {
    Nulls.NONE -> Nulls.NONE
    Nulls.FIRST -> Nulls.LAST
    Nulls.LAST -> Nulls.FIRST
  }
}

fun <T> Order<T>.withNullPrecedence(nullPrecedence: Nulls): Order<T> {
  return when (nullPrecedence) {
    Nulls.NONE -> this
    Nulls.FIRST -> withNullsFirst()
    Nulls.LAST -> withNullsLast()
  }
}
