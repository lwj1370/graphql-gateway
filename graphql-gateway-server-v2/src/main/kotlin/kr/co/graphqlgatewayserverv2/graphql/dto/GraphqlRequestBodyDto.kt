package kr.co.graphqlgatewayweb.graphql.dto

class GraphqlRequestBodyDto {
    private var query: String? = null
    private var operationName: String? = null
    private var variables: Map<String, Any>? = null

    fun getQuery(): String? {
        return query
    }

    fun setQuery(query: String?) {
        this.query = query
    }

    fun getOperationName(): String? {
        return operationName
    }

    fun setOperationName(operationName: String?) {
        this.operationName = operationName
    }

    fun getVariables(): Map<String, Any>? {
        return variables
    }

    fun setVariables(variables: Map<String, Any>?) {
        this.variables = variables
    }
}