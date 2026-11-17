package com.subash.userservice.dto;

// What login and refresh produce: the JSON body for the client, plus the refresh token and
// how many seconds it is valid, which the controller puts into the HttpOnly cookie.
public record AuthResult(LoginResponse body, String refreshToken, long refreshMaxAgeSeconds) {
}
