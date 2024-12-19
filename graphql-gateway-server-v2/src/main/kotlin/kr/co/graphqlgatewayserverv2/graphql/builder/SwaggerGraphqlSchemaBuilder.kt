package kr.co.graphqlgatewayweb.graphql.builder

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.Scalars.*
import graphql.schema.*
import graphql.schema.GraphQLArgument.newArgument
import graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import graphql.schema.GraphQLObjectType.newObject
import io.swagger.models.Model
import io.swagger.models.ModelImpl
import io.swagger.models.Path
import io.swagger.models.Swagger
import io.swagger.models.parameters.BodyParameter
import io.swagger.models.parameters.Parameter
import io.swagger.models.parameters.PathParameter
import io.swagger.models.properties.ArrayProperty
import io.swagger.models.properties.Property
import io.swagger.models.properties.RefProperty
import io.swagger.parser.SwaggerParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors

class SwaggerGraphqlSchemaBuilder {
    private var schemaBuilder: GraphqlSchemaBuilder = GraphqlSchemaBuilder()

    fun swagger(location: String?): SwaggerGraphqlSchemaBuilder {
        return swagger(SwaggerParser().read(location))
    }

    fun swagger(swagger: Swagger): SwaggerGraphqlSchemaBuilder {
        // Types
        val objectTypes = swagger.definitions.entries
            .stream()
            .map { definition: Map.Entry<String, Model> ->
                toGraphQLObjectType(
                    definition.key,
                    definition.value
                )
            }
            .collect(Collectors.toList())

        // Query & Data fetchers
        val queryFields: MutableList<GraphQLFieldDefinition> = ArrayList()
        val dataFetchers: MutableMap<FieldCoordinates, DataFetcher<*>> = HashMap()

        val host = "http://" + swagger.host
        val basePath = swagger.basePath
        swagger.paths.entries.forEach(Consumer<Map.Entry<String, Path>> { path: Map.Entry<String, Path> ->
            val queryField = pathToGraphQLField(path.key, path.value)
            queryFields.add(queryField)
            dataFetchers[FieldCoordinates.coordinates("Query", queryField.name)] =
                buildDataFetcher(host, basePath, path.key, path.value)
        })

        schemaBuilder
            .queryFields(queryFields)
            .objectTypes(objectTypes)
            .dataFetchers(dataFetchers)

        return this
    }

    fun build(): GraphQLSchema {
        return schemaBuilder.build()
    }

    /**
     * Maps Swagger model with GraphQL object type
     * @param name
     * @param swaggerModel
     * @return
     */
    private fun toGraphQLObjectType(name: String, swaggerModel: Model): GraphQLObjectType {
        val fields: List<GraphQLFieldDefinition> = swaggerModel.properties
            .map { this.propertyToGraphQLField(it) }
            .filter { it.isPresent }
            .map{ it.get() }

        return newObject()
            .name(name)
            .fields(fields)
            .build()
    }

    /**
     * Maps Swagger path with GraphQLFieldDefinition
     * @param swaggerPath
     * @return
     */
    private fun pathToGraphQLField(name: String, swaggerPath: Path): GraphQLFieldDefinition {
        val builder = newFieldDefinition()
            .name(pathToType(name))
            .type(
                mapOutputType(
                    "",
                    swaggerPath.get.responses["200"]?.schema ?: throw Exception("Cannot read the Schema")
                ).orElse(null)
            ) // GraphQLString

        swaggerPath.get.parameters?.also { parameters ->
            builder.arguments(
                parameters.map { this.parameterToGraphQLArgument(it) }
            )
        }

        return builder.build()
    }

    /**
     * Maps Swagger property with GraphQLField
     *
     * @param property
     * @return
     */
    private fun propertyToGraphQLField(property: Map.Entry<String, Property>): Optional<GraphQLFieldDefinition> {
        val type = mapOutputType(property.key, property.value)
        return if (!type.isPresent) {
            Optional.empty()
        } else {
            Optional.of(
                newFieldDefinition()
                    .name(property.key)
                    .type(type.get())
                    .build()
            )
        }
    }

    /**
     * Maps Swagger parameter with GraphQLArgument
     * @param parameter
     * @return
     */
    private fun parameterToGraphQLArgument(parameter: Parameter): GraphQLArgument {
        return newArgument()
            .name(parameter.name)
            .type(mapInputType(parameter))
            .build()
    }

    /**
     * Builds DataFetcher for a given query field
     * @return
     */
    private fun buildDataFetcher(host: String, basePath: String, path: String, swaggerPath: Path): DataFetcher<*> {
        val client = OkHttpClient()
        val objectMapper = ObjectMapper()
        val url = host + buildPath(basePath, path)
        val pathParams: List<String> = Optional.ofNullable(swaggerPath.getGet().getParameters()).orElse(emptyList())
            .stream()
            .map(Parameter::getName)
            .collect(Collectors.toList())

        return DataFetcher { dataFetchingEnvironment ->
            val urlParams = pathParams
                .stream()
                .reduce(
                    url
                ) { acc: String?, curr: String? ->
                    url.replace(
                        String.format(
                            "\\{%s}",
                            curr
                        ).toRegex(), dataFetchingEnvironment.getArgument<Any>(curr).toString()
                    )
                }
            val request = Request.Builder().url(urlParams).build()
            val response = client.newCall(request).execute()
            val json: String = response.body?.string() ?: ""
            objectMapper.readValue(json, object : TypeReference<Any>() {})
        }
    }

    /**
     * Returns the path for a given list of steps
     * ["/", "/books/"] will return "/books/"
     *
     * @param paths
     * @return
     */
    private fun buildPath(vararg paths: String?): String? {
        val separator = "/"
        return Arrays.stream(paths).reduce(
            null
        ) { acc: String?, cur: String? ->
            if (acc == null) {
                return@reduce cur
            } else if (acc.lastIndexOf(separator) == acc.length - 1 && cur!!.indexOf(separator) == 0) {
                return@reduce acc + cur!!.substring(1)
            } else {
                return@reduce acc + cur
            }
        }
    }

    /**
     * Maps swagger type GraphQLType
     * @param fieldName
     * @param swaggerProperty
     * @return
     */
    private fun mapOutputType(fieldName: String, swaggerProperty: Property): Optional<GraphQLOutputType> {
        var type: GraphQLOutputType? = null

        val scalarTypes: Map<String, GraphQLScalarType> = mutableMapOf(
            "string" to GraphQLString,
            "integer" to GraphQLInt
        )

        if (isID(fieldName)) {
            type = GraphQLID
        } else if (scalarTypes.containsKey(swaggerProperty.type)) {
            type = scalarTypes[swaggerProperty.type]
        } else if (isReference(swaggerProperty)) {
            type = GraphQLTypeReference.typeRef((swaggerProperty as RefProperty).simpleRef)
        } else if (isArray(swaggerProperty)) {
            type = GraphQLList.list(mapOutputType(fieldName, (swaggerProperty as ArrayProperty).items).orElse(null))
        }

        return Optional.ofNullable(type)
    }

    /**
     * Maps swagger parameter to graphql type
     *
     * @param parameter
     * @return
     */
    private fun mapInputType(parameter: Parameter): GraphQLInputType? {
        val fieldName: String = parameter.getName()
        var swaggerType: String? = null
        if (parameter is PathParameter) {
            swaggerType = (parameter).type
        } else if (parameter is BodyParameter) {
            swaggerType = ((parameter as BodyParameter).schema as ModelImpl).type
        }
        val scalarTypes: Map<String?, GraphQLScalarType> = mutableMapOf(
            "string" to GraphQLString,
            "integer" to GraphQLInt
        )

        return if (isID(fieldName)) GraphQLID
            else scalarTypes[swaggerType]
    }

    /**
     * Returns true if swagger property is Array of types
     * @param swaggerProperty
     * @return
     */
    private fun isArray(swaggerProperty: Property): Boolean {
        return swaggerProperty is ArrayProperty
    }

    /**
     * Returns true if swagger property is Type reference
     * @param swaggerProperty
     * @return
     */
    private fun isReference(swaggerProperty: Property): Boolean {
        return swaggerProperty is RefProperty
    }

    /**
     * Returns true if the fieldName refers to the ID field
     * @param fieldName
     * @return
     */
    private fun isID(fieldName: String): Boolean {
        return (fieldName == "id")
    }

    private fun pathToType(path: String): String {
        return Arrays.stream(path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            .reduce("") { acc: String, curr: String ->
                if ((acc.isBlank())) curr else acc + buildPathName(
                    curr
                )
            }
    }

    private fun buildPathName(name: String): String {
        /**
         * Builds graphql type name from swagger path
         * /books -> books
         * /books/{id} -> booksById
         * /books/library/{id} -> booksWithLibraryById
         */
        val PARAM_FORMAT = "By%s"
        val PATH_FORMAT = "With%s"
        return if (isParam(name)) String.format(
            PARAM_FORMAT,
            capitalize(name.replace("[{}]".toRegex(), ""))
        ) else String.format(PATH_FORMAT, capitalize(name))
    }

    private fun isParam(name: String): Boolean {
        return (name.indexOf("{") == 0 && (name.lastIndexOf("}") == (name.length - 1)))
    }

    private fun capitalize(str: String?): String? {
        if (str.isNullOrEmpty()) {
            return str
        }
        return str.substring(0, 1).uppercase(Locale.getDefault()) + str.substring(1)
    }
}