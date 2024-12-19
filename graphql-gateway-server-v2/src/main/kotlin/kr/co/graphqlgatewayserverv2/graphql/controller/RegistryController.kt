package kr.co.graphqlgatewayweb.graphql.controller

import kr.co.graphqlgatewayweb.graphql.dto.ServiceDto
import kr.co.graphqlgatewayweb.graphql.provider.GraphqlProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/registry")
class RegistryController(
    private val graphqlProvider: GraphqlProvider
) {

    @GetMapping
    fun list(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok().build()
    }

    @PostMapping
    fun register(@RequestBody dto: ServiceDto): ResponseEntity<Map<String, Any>> {
        graphqlProvider.register(dto.name, dto.url)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping
    fun unRegister(@RequestParam name: String): ResponseEntity<Map<String, Any>> {
        graphqlProvider.unregister(name)
        return ResponseEntity.noContent().build()
    }
}