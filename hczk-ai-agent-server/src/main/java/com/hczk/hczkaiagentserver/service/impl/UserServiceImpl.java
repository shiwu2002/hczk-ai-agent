package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.dto.*;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.enums.UserRole;
import com.hczk.hczkaiagentserver.mapper.UserMapper;
import com.hczk.hczkaiagentserver.service.EmailService;
import com.hczk.hczkaiagentserver.service.UserService;
import com.hczk.hczkaiagentserver.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户服务实现类
 * 实现用户登录、注册、查询和更新等业务逻辑
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户登录
     * 校验用户名和密码，生成JWT访问令牌和刷新令牌
     *
     * @param request 登录请求（包含用户名和密码）
     * @return 登录响应（包含令牌和用户信息）
     * @throws RuntimeException 用户不存在或密码错误时抛出
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().getValue());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setRefreshToken(refreshToken);
        response.setUser(convertToDTO(user));
        return response;
    }

    /**
     * 用户注册
     * 校验邮箱验证码，检查用户名和邮箱唯一性，创建新用户
     *
     * @param request 注册请求（包含用户名、密码、邮箱、验证码）
     * @return 注册成功的用户信息
     * @throws RuntimeException 验证码错误、用户名已存在或邮箱已存在时抛出
     */
    @Override
    @Transactional
    public User register(RegisterRequest request) {
        // 校验邮箱验证码
        if (!emailService.verifyCode(request.getEmail(), request.getCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }

        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())) > 0) {
            throw new RuntimeException("用户名已存在");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail())) > 0) {
            throw new RuntimeException("邮箱已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRole(UserRole.USER);
        // 确保新建用户默认正常状态
        if (user.getStatus() == null) {
            user.setStatus(0);
        }
        // userId 由 MyBatis-Plus 雪花算法自动赋值（@TableId(type = IdType.ASSIGN_ID)）
        userMapper.insert(user);
        return user;
    }

    /**
     * 根据用户名获取当前用户信息
     *
     * @param username 用户名
     * @return 用户DTO信息
     * @throws RuntimeException 用户不存在时抛出
     */
    @Override
    public UserDTO getCurrentUser(String username) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return convertToDTO(user);
    }

    /**
     * 获取所有用户列表
     *
     * @return 用户列表
     */
    @Override
    public List<User> getAllUsers() {
        return userMapper.selectList(null);
    }

    /**
     * 更新用户信息
     * 仅更新非空字段（手机号、公司名称）
     *
     * @param userId  用户ID（雪花ID）
     * @param userDTO 用户更新数据
     * @return 更新后的用户信息
     * @throws RuntimeException 用户不存在时抛出
     */
    @Override
    @Transactional
    public User updateUser(String userId, UserDTO userDTO) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (userDTO.getPhoneNumber() != null) {
            user.setPhoneNumber(userDTO.getPhoneNumber());
        }
        if (userDTO.getCompanyName() != null) {
            user.setCompanyName(userDTO.getCompanyName());
        }
        userMapper.updateById(user);
        return user;
    }

    /**
     * 将User实体转换为UserDTO
     *
     * @param user 用户实体
     * @return 用户DTO
     */
    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setUserId(user.getUserId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole().getValue());
        dto.setBalance(user.getBalance());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setCompanyName(user.getCompanyName());
        dto.setStatus(user.getStatus());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }

    /**
     * 重置密码（忘记密码）
     * 校验邮箱验证码，通过后更新用户密码
     *
     * @param request 忘记密码请求（包含邮箱、验证码、新密码）
     * @throws RuntimeException 验证码错误、邮箱不存在时抛出
     */
    @Override
    @Transactional
    public void resetPassword(ForgotPasswordRequest request) {
        // 校验邮箱验证码
        if (!emailService.verifyCode(request.getEmail(), request.getCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));
        if (user == null) {
            throw new RuntimeException("该邮箱未注册");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }
}
