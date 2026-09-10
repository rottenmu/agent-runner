package com.zimo.framework.ai.mcp;

public record McpJsonRpcResponse(String jsonrpc, Object id, Object result, Object error) {
    public static McpJsonRpcResponse ok(Object id, Object result) {
        return new McpJsonRpcResponse("2.0", id, result, null);
    }

    public static McpJsonRpcResponse error(Object id, int code, String message) {
        return new McpJsonRpcResponse("2.0", id, null, new McpJsonRpcError(code, message));
    }

    private record McpJsonRpcError(int code, String message) {
    }
}
