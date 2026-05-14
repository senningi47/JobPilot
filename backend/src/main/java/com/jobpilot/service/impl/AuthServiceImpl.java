package com.jobpilot.service.impl;

import com.jobpilot.dto.auth.AuthResponse;
import com.jobpilot.dto.auth.LoginRequest;
import com.jobpilot.dto.auth.RegisterRequest;
import com.jobpilot.entity.UserEntity;
import com.jobpilot.exception.BusinessException;
import com.jobpilot.repository.UserRepository;
import com.jobpilot.service.AuthService;
import com.jobpilot.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw BusinessException.userExists();
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw BusinessException.userExists();
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setMajor(request.getMajor());
        user.setGraduationYear(request.getGraduationYear());

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getEmail());

        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo(
                user.getId(), user.getUsername(), user.getEmail(), user.getMajor()
        );
        return new AuthResponse(token, userInfo);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(BusinessException::unauthorized);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw BusinessException.unauthorized();
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getEmail());

        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo(
                user.getId(), user.getUsername(), user.getEmail(), user.getMajor()
        );
        return new AuthResponse(token, userInfo);
    }

    @Override
    public AuthResponse.UserInfo getCurrentUser(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound(2001, "用户不存在"));

        return new AuthResponse.UserInfo(
                user.getId(), user.getUsername(), user.getEmail(), user.getMajor()
        );
    }
}
