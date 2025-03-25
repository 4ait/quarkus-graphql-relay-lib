# Quarkus GraphQL Relay Library

This library provides an implementation of
the [Relay GraphQL specification](https://relay.dev/docs/guides/graphql-server-specification/) for Quarkus applications.
It enables developers to build GraphQL APIs that are compatible with Relay, a JavaScript framework for building
data-driven React applications.

## Features

- Complete implementation of Relay's Node interface
- Cursor-based pagination with Connection pattern
- Secure ID handling with encryption
- Built-in ordering capabilities
- Support for Quarkus' native compilation
- Integration with JPA/Hibernate for database operations

## Installation

Add the dependency to your project:

```xml

<dependency>
  <groupId>ru.code4a</groupId>
  <artifactId>quarkus-graphql-relay</artifactId>
  <version>{version}</version>
</dependency>
```

## Configuration

Add the following properties to your `application.properties`:

```properties
# 32 bytes Base64-encoded key for node ID encryption
ru.code4a.graphql.relay.node.id.cipher.key-32-bytes-base64=your-base64-encoded-key
# Base64-encoded salt for node ID encryption
ru.code4a.graphql.relay.node.id.cipher.salt-base64=your-base64-encoded-salt
# 32 bytes Base64-encoded key for cursor encryption
ru.code4a.graphql.relay.cursor.cipher.key-32-bytes-base64=your-base64-encoded-key
# Base64-encoded salt for cursor encryption
ru.code4a.graphql.relay.cursor.cipher.salt-base64=your-base64-encoded-salt
```

## Usage

### Defining a GraphQL Node Entity

1. Create an entity class:

```kotlin
@Entity
class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private var id: Long = 0

  var name: String = ""
  var email: String = ""

  fun getId(): Long = id
}
```

2. Create a GraphQL object that implements `GraphqlNode`:

```kotlin
@GraphqlRelayNodeEntityById
@Name("User") // This is the GraphQL type name
class UserGQLObject(
  private val user: User,
  private val builder: UserGQLObjectBuilder
) : GraphqlNode {

  // You must manually implement the ID resolution
  // This allows for access control checks before exposing the ID
  @get:Id
  @get:AccessGrantedForTags([AccessTag.FOR_CLIENT])
  override val id by lazy(LazyThreadSafetyMode.NONE) {
    builder.graphqlRelayNodeManager.getNodeIdForObject(
      user,
      this
    )
  }

  val name: String
    get() = user.name

  val email: String
    get() = user.email
}
```

### Implementing Required Services

Implement the `GraphqlRelayEntityGQLObjectBuilderGlobalService`:

```kotlin
// Note: This service is loaded via Java ServiceLoader because it's used during native compilation
// You must register it in META-INF/services
class MyEntityGQLObjectBuilderService : GraphqlRelayEntityGQLObjectBuilderGlobalService {
  override fun create(graphqlRelayNodeEntityObjectClass: Class<*>):
    GraphqlRelayEntityGQLObjectBuilderGlobalService.GraphqlRelayEntityGQLObjectBuilderItem {
    return when (graphqlRelayNodeEntityObjectClass) {
      UserGQLObject::class.java -> object :
        GraphqlRelayEntityGQLObjectBuilderGlobalService.GraphqlRelayEntityGQLObjectBuilderItem {
        override val entityClass: Class<*> = User::class.java

        override fun get(entity: Any): Any {
          // Create your builder and pass it to the GQL object
          val builder = UserGQLObjectBuilder(graphqlRelayNodeManager)
          return UserGQLObject(entity as User, builder)
        }
      }
      // Add other entity mappings here
      else -> throw IllegalArgumentException("Unknown GraphQL object class: ${graphqlRelayNodeEntityObjectClass.name}")
    }
  }
}
```

Implement the `GraphqlRelayCipher`:

```kotlin
@ApplicationScoped
class MyCipher : GraphqlRelayCipher {
  override fun encrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    // Implement encryption (e.g., using AES/GCM)
    // This is just a placeholder - use a secure encryption method
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val keySpec = SecretKeySpec(key, "AES")
    val paramSpec = GCMParameterSpec(128, iv)
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, paramSpec)
    return cipher.doFinal(input)
  }

  override fun decrypt(input: ByteArray, key: ByteArray): ByteArray {
    // Implement decryption
    // This method should extract the IV from the input and use it for decryption
    // This is just a placeholder - use a secure decryption method
    val iv = input.sliceArray(0 until 12) // Example for GCM
    val encryptedData = input.sliceArray(12 until input.size)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val keySpec = SecretKeySpec(key, "AES")
    val paramSpec = GCMParameterSpec(128, iv)
    cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec)
    return cipher.doFinal(encryptedData)
  }
}
```

Implement the `GraphqlRelayEntityGetterFactoryService`:

```kotlin
// Note: This service is loaded via Java ServiceLoader
class MyEntityGetterFactoryService : GraphqlRelayEntityGetterFactoryService {

  // inject this with Arc
  lateinit var entityManager: EntityManager
  lateinit var securityService: SecurityService

  override fun createGetter(
    graphqlRelayNodeEntityObjectClass: Class<*>,
    entityClass: Class<*>
  ): GraphqlRelayEntityGetterFactoryService.GraphqlRelayEntityGetter {
    return when (graphqlRelayNodeEntityObjectClass) {
      UserGQLObject::class.java -> object : GraphqlRelayEntityGetterFactoryService.GraphqlRelayEntityGetter {
        override fun getEntityById(id: Long): Any? {
          val user = entityManager.find(User::class.java, id)

          // Check if current user has permission to access this entity
          if (user != null && !securityService.hasAccessTo(user)) {
            return null
          }

          return user
        }
      }
      // Add other entity mappings here
      else -> object : GraphqlRelayEntityGetterFactoryService.GraphqlRelayEntityGetter {
        override fun getEntityById(id: Long): Any? {
          return null // Default implementation returns null
        }
      }
    }
  }
}
```

You must register this service in
`META-INF/services/ru.code4a.graphql.relay.interfaces.GraphqlRelayEntityGetterFactoryService`
with the fully qualified class name of your implementation.

### Defining GraphQL Queries

Create a GraphQL resource class for the Node interface:

```kotlin
@GraphQLApi
@AuthorizationRequired // Ensures all endpoints require authorization
class NodeGraphqlEndpoint(
  private val graphqlRelayNodeManager: GraphqlRelayNodeManager
) {
  @AccessGranted // Access control annotation
  @TransactionalQuery // Manages transaction for you
  @Query // Marks this as a GraphQL query
  fun node(id: String): GraphqlNode? {
    // This endpoint resolves any node by its global ID
    // Security is handled by the GraphqlRelayEntityGetter implementation
    return graphqlRelayNodeManager.getGraphqlNodeByNodeId(id)
  }
}
```

Create a GraphQL resource for entity-specific queries:

```kotlin
@GraphQLApi
@AuthorizationRequired
class UserResource(
  private val graphqlRelayNodeManager: GraphqlRelayNodeManager,
  private val connectionBuilder: ConnectionGQLObjectFromQueryBuilder,
  private val entityManager: EntityManager
) {
  @AccessGrantedForTags([AccessTag.FOR_CLIENT]) // More specific access control
  @TransactionalQuery
  @Query("users")
  fun getUsers(
    @DefaultValue("10") first: Int?,
    last: Int?,
    after: String?,
    before: String?,
    order: OrderIdOnlyGQLInput?
  ): ConnectionGQLObject<UserGQLObject> {
    val cb = entityManager.criteriaBuilder
    val query = cb.createQuery(User::class.java)
    val root = query.from(User::class.java)
    query.select(root)

    return connectionBuilder.build(
      query = query,
      first = first,
      last = last,
      after = after,
      before = before,
      order = listOfNotNull(order),
      builder = object : GraphqlRelayResponseObjectBuilder<User, UserGQLObject> {
        override fun get(from: User): UserGQLObject {
          val builder = UserGQLObjectBuilder(graphqlRelayNodeManager)
          return UserGQLObject(from, builder)
        }
      }
    )
  }
}
```

## Working with GraphqlRelayNodeEntityById

The `GraphqlRelayNodeEntityById` annotation is a critical part of the Node interface implementation. It associates a
unique numeric ID with each GraphQL type in your system.

### Node ID Generation

By default, if you don't specify a `nodeId` value, the library will generate one by hashing the GraphQL type name:

```kotlin
@GraphqlRelayNodeEntityById // Uses default nodeId generation
@Name("User")
class UserGQLObject(...) : GraphqlNode {
  // ...
}
```

The generated ID is deterministic, meaning the same GraphQL type name will always produce the same ID. This works well
for most cases, but there are important considerations.

### Best Practices During Development

1. **Initial Development**: When first creating a new entity type, let the library generate the ID automatically:

   ```kotlin
   @GraphqlRelayNodeEntityById
   @Name("Product")
   class ProductGQLObject(...) : GraphqlNode {
       // ...
   }
   ```

2. **Test for Collisions**: Before deploying to production, check for potential ID collisions by logging or testing the
   generated IDs.

3. **Manually Set IDs if Collisions Occur**: If you discover ID collisions during testing, manually set distinct IDs:

   ```kotlin
   @GraphqlRelayNodeEntityById(nodeId = 34567890123456L)
   @Name("Product")
   class ProductGQLObject(...) : GraphqlNode {
       // ...
   }
   ```

4. **Document Your Node IDs**: Keep a registry of assigned node IDs in your project documentation to avoid future
   collisions.

### Important Warnings

- **NEVER CHANGE NODE IDs AFTER RELEASE**: Once your application is in production, changing a nodeId will break all
  client references to that type.

- **NEVER CHANGE GraphQL TYPE NAMES**: Changing the `@Name` value after release will also generate a new ID if you're
  using the default ID generation.

- **HANDLE WITH CARE DURING REFACTORING**: When renaming classes or moving them between packages, ensure the GraphQL
  type name and nodeId remain constant.

### Example: Safe Refactoring

If you need to refactor a class that's already in production:

```kotlin
// Original class - already in production
@GraphqlRelayNodeEntityById(nodeId = 7239874598237459L)
@Name("Customer")
class CustomerGQLObject(...) : GraphqlNode {
  // ...
}

// Refactored class - maintaining same nodeId and GraphQL type name
@GraphqlRelayNodeEntityById(nodeId = 7239874598237459L) // SAME ID!
@Name("Customer") // SAME NAME!
class EnhancedCustomerGQLObject(...) : GraphqlNode {
  // ...
}
```

This ensures existing client applications can still resolve Customer nodes with previously cached IDs.

## Architecture

### Node Global ID System

This library implements the Relay Node Interface pattern, which provides globally unique IDs that can identify any
object in your system. These IDs are:

1. Encrypted to prevent exposing internal database IDs
2. Include both the entity type information and the entity ID
3. Can be used to resolve any node through a single endpoint

### Cursor-Based Pagination

The library provides cursor-based pagination through the `ConnectionGQLObjectFromQueryBuilder` class. This implements
the Connection pattern from the Relay GraphQL specification and offers:

1. Forward and backward pagination
2. Efficient database queries using Hibernate's keyset pagination
3. Secure cursors that encapsulate query positions

### Custom Ordering

You can define custom ordering fields by creating input classes with the `GraphqlConnectionOrderField` annotation:

```kotlin
@Input("OrderUserByName")
class OrderUserByNameGQLInput(
  @get:GraphqlConnectionOrderField(
    fieldType = GraphqlConnectionOrderField.Type.STRING,
    entityFieldName = "name",
    entityValueGetter = "getName"
  )
  var name: OrderTypeGQLEnum?
)
```

Then use it in your queries:

```kotlin
@Query("users")
fun getUsers(
  @DefaultValue("10") first: Int?,
  last: Int?,
  after: String?,
  before: String?,
  orderByName: OrderUserByNameGQLInput?
): ConnectionGQLObject<UserGQLObject> {
  // ... setup criteria query

  return connectionBuilder.build(
    query = query,
    first = first,
    last = last,
    after = after,
    before = before,
    order = listOfNotNull(orderByName),
    builder = object : GraphqlRelayResponseObjectBuilder<User, UserGQLObject> {
      override fun get(from: User): UserGQLObject {
        return UserGQLObject(from)
      }
    }
  )
}
```

## Security Considerations

### Access Control Integration

The `GraphqlRelayEntityGetter` interface can be used to implement access control checks:

1. Perform authorization checks before returning entities
2. Return `null` for entities that shouldn't be accessible to the current user
3. Integrate with your existing security framework

### Secure IDs and Cursors

This library handles secure IDs by encrypting entity identifiers. It uses:

1. AES encryption for Node IDs and Connection cursors
2. Separate keys and salts for Node IDs and Cursors
3. IV generation based on data hash to ensure consistent encryption/decryption

Always use secure, randomly generated keys and salts in your production environment.

### Access Control Annotations

As shown in the examples, the library integrates well with access control frameworks:

```kotlin
@get:AccessGrantedForTags([AccessTag.FOR_CLIENT])
override val id by lazy(LazyThreadSafetyMode.NONE) {
  // ID resolution happens only when the user has sufficient permissions
  builder.graphqlRelayNodeManager.getNodeIdForObject(entity, this)
}
```

## License

Apache 2.0

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
