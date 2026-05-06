package ru.code4a.graphql.relay.services

import org.hibernate.Hibernate

internal fun getHibernateProxySafeReflectionReceiver(
  obj: Any,
  declaringClass: Class<*>
): Any {
  return if (declaringClass.isInstance(obj)) {
    obj
  } else {
    Hibernate.unproxy(obj)
  }
}
