package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.dto.*;
import com.hczk.hczkaiagentserver.entity.User;

import java.util.List;

public interface UserService {
    LoginResponse login(LoginRequest request);
    User register(RegisterRequest request);
    UserDTO getCurrentUser(String username);
    List<User> getAllUsers();
    User updateUser(Long id, UserDTO userDTO);
}
