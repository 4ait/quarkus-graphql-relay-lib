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

Configure any secrets required by your `GraphqlRelayDeterministicCipher` implementation in `application.properties` (the
library itself no longer expects specific property names). For example:

```properties
# 32 bytes Base64-encoded key for encrypting IDs and cursors
ru.code4a.graphql.relay.cipher.key-32-bytes-base64=your-base64-encoded-key
# Optional salt used to derive deterministic nonces/IVs inside the cipher
ru.code4a.graphql.relay.cipher.salt-base64=your-base64-encoded-salt
```

You can provide multiple cipher beans with different configuration blocks if you need to isolate cursor and node ID
encryption.

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

Provide a deterministic cipher bean that owns its own key management. The services now expect the cipher to derive and
encode any IV or nonce internally, so the same input always encrypts to the same output.

Implement the `GraphqlRelayDeterministicCipher`:

```kotlin
@ApplicationScoped
class MyCipher(
  @ConfigProperty(name = "ru.code4a.graphql.relay.cipher.key-32-bytes-base64")
  private val secretKeyBase64: String,
  @ConfigProperty(name = "ru.code4a.graphql.relay.cipher.salt-base64")
  private val saltBase64: String,
) : GraphqlRelayDeterministicCipher {
  private val secretKeySpec by lazy {
    val bytes = Base64.getDecoder().decode(secretKeyBase64)
    require(bytes.size == 32) { "Secret Key must be 32 bytes" }
    SecretKeySpec(bytes, "AES")
  }

  private val saltBytes by lazy { Base64.getDecoder().decode(saltBase64) }

  override fun encrypt(input: ByteArray): ByteArray {
    val iv = deriveIv(input)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, GCMParameterSpec(128, iv))

    // Prepend IV so it can be extracted during decryption
    return iv + cipher.doFinal(input)
  }

  override fun decrypt(input: ByteArray): ByteArray {
    val iv = input.copyOfRange(0, 12)
    val encryptedPayload = input.copyOfRange(12, input.size)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, GCMParameterSpec(128, iv))

    return cipher.doFinal(encryptedPayload)
  }

  private fun deriveIv(data: ByteArray): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(saltBytes + data)
    return hash.copyOfRange(0, 12) // 96-bit IV for GCM
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

This library handles secure IDs by encrypting entity identifiers. With the deterministic cipher:

1. AES/GCM (or another modern cipher) can be used for Node IDs and Connection cursors
2. IV/nonce derivation must happen inside your `GraphqlRelayDeterministicCipher` implementation (and be included with
   the ciphertext if needed)
3. Keep keys in a secrets store; you can inject different keys into multiple cipher beans if you want separation between
   Node IDs and cursors

Always use secure, randomly generated keys and salts in your production environment and rotate them carefully.

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
