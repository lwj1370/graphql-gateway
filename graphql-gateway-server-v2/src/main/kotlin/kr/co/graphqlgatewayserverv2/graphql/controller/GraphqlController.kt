package kr.co.graphqlgatewayweb.graphql.controller

import kr.co.graphqlgatewayweb.graphql.dto.GraphqlRequestBodyDto
import kr.co.graphqlgatewayweb.graphql.provider.GraphqlProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/graphql")
class GraphqlController(
    private val graphqlProvider: GraphqlProvider,
) {

    @PostMapping
    fun graphql(@RequestBody request: GraphqlRequestBodyDto): ResponseEntity<*>? {
        if (request.getQuery() == null) {
            request.setQuery("")
        }
        val result: Any = graphqlProvider.getGraphQL()?.execute(request.getQuery()) ?: Any()
//        println(result)
        return ResponseEntity.ok(result)
    }
}