package com.jobpilot.service;

import com.jobpilot.dto.auth.AuthResponse;
import com.jobpilot.dto.auth.LoginRequest;
import com.jobpilot.dto.auth.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse.UserInfo getCurrentUser(Long userId);
}
