package interview.guide.modules.auth.dto;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresInSeconds,
    UserDTO user
) {
}
