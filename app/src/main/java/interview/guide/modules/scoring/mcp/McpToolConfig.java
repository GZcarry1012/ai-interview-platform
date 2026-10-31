package interview.guide.modules.scoring.mcp;

import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 工具注册
 *
 * 通过 spring-ai-starter-mcp-server-webmvc 暴露 SSE 端点（默认 /sse），
 * MCP 客户端（Claude、Inspector 等）可发现并调用这些工具
 */
@Configuration
public class McpToolConfig {

  @Bean
  public MethodToolCallbackProvider interviewMcpToolProvider(InterviewMcpTools tools) {
    return MethodToolCallbackProvider.builder().toolObjects(tools).build();
  }
}
