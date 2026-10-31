package interview.guide.modules.scoring.service;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 评分标准文本提供者（Agent 工具与 MCP 工具共用）
 */
@Component
public class StandardTextProvider {

  private volatile String cached;

  public String get() {
    if (cached == null) {
      synchronized (this) {
        if (cached == null) {
          cached = load();
        }
      }
    }
    return cached;
  }

  private String load() {
    try (var in = new DefaultResourceLoader()
        .getResource("classpath:prompts/scoring-standards.md").getInputStream()) {
      return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("加载评分标准失败", e);
    }
  }
}
