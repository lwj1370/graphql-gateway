package kr.co.graphqlgatewayweb.graphql.provider

import graphql.GraphQL
import io.swagger.models.Swagger
import io.swagger.parser.SwaggerParser
import kr.co.graphqlgatewayweb.graphql.builder.SwaggerGraphqlSchemaBuilder
import org.springframework.stereotype.Component

@Component
class GraphqlProvider {

    private val services: MutableMap<String, Swagger> = mutableMapOf()
    private var graphQL: GraphQL? = null

    /**
     * Registers a REST service
     * @param name
     * @param location
     */
    fun register(name: String, location: String) {
        services[name] = SwaggerParser().read(location)
        load()
    }

    /**
     * Unregisters a REST service
     * @param name
     */
    fun unregister(name: String) {
        services.remove(name)
        load()
    }

    /**
     * Returns the list of registered services
     */
    fun services(): Collection<String> {
        return services.keys
    }

    /**
     * Loads REST services into the GraphQL schema
     */
    private fun load() {
        val graphQLConverter = SwaggerGraphqlSchemaBuilder()
        services.values.forEach { swagger -> graphQLConverter.swagger(swagger) }
        graphQL = GraphQL.newGraphQL(graphQLConverter.build()).build()
    }

    /**
     * Returns the GraphQL instance
     */
    fun getGraphQL(): GraphQL? {
        // TODO: Handle the case when graphQL is null (e.g., no service registered)
        return graphQL
    }

}