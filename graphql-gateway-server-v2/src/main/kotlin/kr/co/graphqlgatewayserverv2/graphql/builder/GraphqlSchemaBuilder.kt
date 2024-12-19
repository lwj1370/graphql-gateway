package kr.co.graphqlgatewayweb.graphql.builder

import graphql.schema.*
import graphql.schema.GraphQLCodeRegistry.newCodeRegistry
import graphql.schema.GraphQLObjectType.newObject
import graphql.schema.GraphQLSchema.newSchema
import org.slf4j.LoggerFactory
import java.util.function.Consumer


class GraphqlSchemaBuilder {

    private val QUERY_STR = "Query"
    private val MUTATION_STR = "Mutation"

    /** Object types.  */
    private val objectTypesMap: MutableMap<String, GraphQLObjectType> = mutableMapOf()

    /** Interface types.  */
    private val interfaceTypesMap: MutableMap<String, GraphQLInterfaceType> = mutableMapOf()

    /** Query fields.  */
    private val queryFieldsMap: MutableMap<String, GraphQLFieldDefinition> = mutableMapOf()

    /** Mutation fields.  */
    private val mutationFieldsMap: MutableMap<String, GraphQLFieldDefinition> = mutableMapOf()

    /** Data fetchers.  */
    private val dataFetchersMap: MutableMap<FieldCoordinates, DataFetcher<*>> = mutableMapOf()

    /** Type resolvers.  */
    private val typeResolversMap: MutableMap<String, TypeResolver> = mutableMapOf()

    private val log = LoggerFactory.getLogger(GraphqlSchemaBuilder::class.java)

    fun objectType(objectType: GraphQLObjectType): GraphqlSchemaBuilder {
        if (!objectTypesMap.containsKey(objectType.name)) {
            objectTypesMap[objectType.name] = objectType
        } else {
            log.warn("The object type '{}' has already been defined, its definition will be ignored", objectType.name)
        }

        return this
    }

    fun objectTypes(objectTypes: Collection<GraphQLObjectType>): GraphqlSchemaBuilder {
        objectTypes.forEach { this.objectType(it) }

        return this
    }

    fun interfaceType(interfaceType: GraphQLInterfaceType): GraphqlSchemaBuilder {
        if (!interfaceTypesMap.containsKey(interfaceType.name)) {
            interfaceTypesMap[interfaceType.name] = interfaceType
        } else {
            log.warn(
                "The interface type '{}' has already been defined, its definition will be ignored",
                interfaceType.name
            )
        }

        return this
    }

    fun interfaceTypes(interfaceTypes: Collection<GraphQLInterfaceType>): GraphqlSchemaBuilder {
        interfaceTypes.stream().forEach { interfaceType: GraphQLInterfaceType ->
            this.interfaceType(interfaceType)
        }

        return this
    }

    fun queryField(fieldDefinition: GraphQLFieldDefinition): GraphqlSchemaBuilder {
        if (!queryFieldsMap.containsKey(fieldDefinition.name)) {
            queryFieldsMap[fieldDefinition.name] = fieldDefinition
        } else {
            log.warn(
                "The query field '{}' has already been defined, its definition will be ignored",
                fieldDefinition.name
            )
        }

        return this
    }

    fun queryFields(fieldDefinitions: Collection<GraphQLFieldDefinition>): GraphqlSchemaBuilder {
        fieldDefinitions.stream().forEach { fieldDefinition: GraphQLFieldDefinition ->
            this.queryField(fieldDefinition)
        }

        return this
    }

    fun mutationField(fieldDefinition: GraphQLFieldDefinition): GraphqlSchemaBuilder {
        if (!mutationFieldsMap.containsKey(fieldDefinition.name)) {
            mutationFieldsMap[fieldDefinition.name] = fieldDefinition
        } else {
            log.warn(
                "The mutation field '{}' has already been defined, its definition will be ignored",
                fieldDefinition.name
            )
        }

        return this
    }

    fun mutationFields(fieldDefinitions: Collection<GraphQLFieldDefinition>): GraphqlSchemaBuilder {
        fieldDefinitions.stream().forEach { fieldDefinition: GraphQLFieldDefinition ->
            mutationField(fieldDefinition)
        }

        return this
    }

    fun dataFetcher(
        coordinates: FieldCoordinates,
        dataFetcher: DataFetcher<*>
    ): GraphqlSchemaBuilder {
        if (!dataFetchersMap.containsKey(coordinates)) {
            dataFetchersMap[coordinates] = dataFetcher
        } else {
            log.warn("The data fetcher for '{}' has already been defined, its definition will be ignored", coordinates)
        }

        return this
    }

    fun dataFetchers(dataFetchers: Map<FieldCoordinates, DataFetcher<*>>): GraphqlSchemaBuilder {
        dataFetchers.entries.forEach { entry: Map.Entry<FieldCoordinates, DataFetcher<*>> ->
            this.dataFetcher(entry.key, entry.value)
        }

        return this
    }

    fun typeResolver(typeName: String, typeResolver: TypeResolver): GraphqlSchemaBuilder {
        if (!typeResolversMap.containsKey(typeName)) {
            typeResolversMap[typeName] = typeResolver
        } else {
            log.warn(
                "The type resolver for '{}' has already been defined, its definition will be ignored",
                typeName
            )
        }

        return this
    }

    fun typeResolvers(typeResolvers: Map<String, TypeResolver>): GraphqlSchemaBuilder {
        typeResolvers.entries.stream().forEach { entry: Map.Entry<String, TypeResolver> ->
            this.typeResolver(entry.key, entry.value)
        }

        return this
    }

    fun build(): GraphQLSchema {
        val schemaBuilder: GraphQLSchema.Builder = newSchema()

        // Interface types
        interfaceTypesMap.values.stream().forEach(Consumer { additionalType ->
            schemaBuilder.additionalType(additionalType)
        })

        // Object types
        objectTypesMap.values.stream().forEach(Consumer { additionalType ->
            schemaBuilder.additionalType(
                additionalType
            )
        })

        // Query
        val query: GraphQLObjectType.Builder = newObject().name(QUERY_STR)
        queryFieldsMap.values.stream().forEach(query::field)

        // Mutation
        val mutation: GraphQLObjectType.Builder = newObject().name(MUTATION_STR);
        mutationFieldsMap.values.stream().forEach(mutation::field);

        // Code registry
        val codeRegistry: GraphQLCodeRegistry.Builder = newCodeRegistry()
        dataFetchersMap.entries.stream().forEach { entry ->
            codeRegistry.dataFetcher(entry.key, entry.value)
        }
        typeResolversMap.entries.stream().forEach { entry ->
            codeRegistry.typeResolver(entry.key, entry.value)
        }
        schemaBuilder.codeRegistry(codeRegistry.build())

        return schemaBuilder
            .query(query.build())
//            .mutation(mutation.build())
            .build()
    }

}

