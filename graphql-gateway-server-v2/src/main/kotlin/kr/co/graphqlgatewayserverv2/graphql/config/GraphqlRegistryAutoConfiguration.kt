package kr.co.graphqlgatewayweb.graphql.config

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import kr.co.graphqlgatewayweb.graphql.builder.GraphqlSchemaBuilder
import kr.co.graphqlgatewayweb.graphql.dto.ServiceDto
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

@Configuration
@ConfigurationProperties(prefix = "graphql.registry")
@ConditionalOnProperty("graphql.registry.uri")
class GraphQLRegistryAutoConfiguration(
    private val ctx: ApplicationContext,
    private val env: Environment
): ApplicationListener<ApplicationReadyEvent> {

    companion object {
        private val LOG = LoggerFactory.getLogger(GraphQLRegistryAutoConfiguration::class.java)
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    lateinit var uri: String

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        val appName = ctx.id!!
        try {
            register(appName, uri)
        } catch (e: JsonProcessingException) {
            LOG.error("ERRORS registering {} in {}", appName, uri, e)
        }
    }

    /**
     * TODO
     *      REFACTOR!!!!!
     * Register the service in Graphql Gateway
     * @param appName
     * @param uri
     * @return
     */
    private fun register(appName: String, uri: String): Boolean {
        val client = OkHttpClient()
        val serviceDto = ServiceDto(
            name = appName,
            url = "http://localhost:" + env.getProperty("local.server.port") + "/v2/api-docs"
        )
        val objectMapper = ObjectMapper()
        val body = RequestBody.create(JSON, objectMapper.writeValueAsString(serviceDto))
        val request = Request.Builder()
            .url("$uri/registry")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 200 || response.code == 204) {
                    true
                } else {
                    LOG.error("ERRORS registering {} in {}\n", appName, uri, response.body?.string())
                    false
                }
            }
        } catch (e: IOException) {
            LOG.error("ERRORS registering {} in {}", appName, uri, e)
            false
        }
    }

    private fun getServiceHost(): String? {
        return try {
            InetAddress.getLocalHost().hostName
        } catch (e: UnknownHostException) {
            LOG.error("Errors getting the host IP", e)
            null
        }
    }
}
