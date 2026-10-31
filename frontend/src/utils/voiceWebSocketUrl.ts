/**
 * 与 REST API 使用同一入口，避免将服务器的 localhost 地址当成浏览器地址。
 *
 * @param token 访问令牌（由调用方注入，供 WS 握手鉴权；测试环境传 undefined）
 */
export function buildVoiceWebSocketUrl(
  sessionId: number,
  apiBaseUrl: string,
  pageUrl: string,
  token?: string | null,
): string {
  const apiUrl = new URL(apiBaseUrl || '/', pageUrl);
  const socketUrl = new URL(apiUrl);
  socketUrl.pathname = `${apiUrl.pathname.replace(/\/+$/, '')}/ws/voice-interview/${sessionId}`;
  socketUrl.search = '';
  socketUrl.hash = '';
  socketUrl.protocol = apiUrl.protocol === 'https:' ? 'wss:' : 'ws:';
  // WS 握手鉴权：后端 WsJwtHandshakeInterceptor 校验访问令牌
  if (token) {
    socketUrl.searchParams.set('token', token);
  }
  return socketUrl.toString();
}
