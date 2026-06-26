package com.vaspshow.backend.service;

import com.vaspshow.backend.config.AuthProperties;
import com.vaspshow.backend.dto.AuthSessionResponse;
import com.vaspshow.backend.dto.AuthUserResponse;
import com.vaspshow.backend.dto.LoginRequest;
import com.vaspshow.backend.dto.RegisterRequest;
import com.vaspshow.backend.exception.ApiException;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
  public static final String ROLE_ADMIN = "ADMIN";
  public static final String ROLE_USER = "USER";
  public static final String ROLE_GUEST = "GUEST";

  private final AuthProperties properties;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final SecureRandom secureRandom = new SecureRandom();
  private final Map<String, AuthUserResponse> sessions = new ConcurrentHashMap<>();
  private volatile String jdbcUrl;

  public AuthService(AuthProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  public void initialize() {
    try (Connection connection = openConnection();
         Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE IF NOT EXISTS APP_USERS (
            USERNAME VARCHAR(64) PRIMARY KEY,
            DISPLAY_NAME VARCHAR(120) NOT NULL,
            PASSWORD_HASH VARCHAR(120) NOT NULL,
            ROLE VARCHAR(32) NOT NULL,
            ACTIVE BOOLEAN NOT NULL DEFAULT TRUE,
            CREATED_AT VARCHAR(40) NOT NULL
          )
          """);
      seedUser(connection, "superadmin", "超级管理员", properties.getSuperAdminPassword(), ROLE_SUPER_ADMIN);
      seedUser(connection, "admin", "管理员", properties.getAdminPassword(), ROLE_ADMIN);
    } catch (SQLException ex) {
      throw new IllegalStateException("初始化认证数据库失败: " + ex.getMessage(), ex);
    }
  }

  public AuthSessionResponse register(RegisterRequest request) {
    String username = normalizedUsername(request == null ? null : request.username());
    String displayName = normalizeDisplayName(request == null ? null : request.displayName(), username);
    String password = validatePassword(request == null ? null : request.password());
    String sql = """
        INSERT INTO APP_USERS (USERNAME, DISPLAY_NAME, PASSWORD_HASH, ROLE, ACTIVE, CREATED_AT)
        VALUES (?, ?, ?, ?, TRUE, ?)
        """;
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, username);
      statement.setString(2, displayName);
      statement.setString(3, passwordEncoder.encode(password));
      statement.setString(4, ROLE_USER);
      statement.setString(5, Instant.now().toString());
      statement.executeUpdate();
      return createSession(new AuthUserResponse(username, displayName, ROLE_USER, true));
    } catch (SQLException ex) {
      if ("23505".equals(ex.getSQLState())) {
        throw new ApiException(HttpStatus.CONFLICT, "用户名已存在");
      }
      throw authDatabaseError(ex);
    }
  }

  public AuthSessionResponse login(LoginRequest request) {
    String username = normalizedUsername(request == null ? null : request.username());
    String password = request == null ? "" : String.valueOf(request.password());
    String sql = """
        SELECT USERNAME, DISPLAY_NAME, PASSWORD_HASH, ROLE, ACTIVE
        FROM APP_USERS WHERE USERNAME = ?
        """;
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, username);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next() || !rs.getBoolean("ACTIVE") || !passwordEncoder.matches(password, rs.getString("PASSWORD_HASH"))) {
          throw new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return createSession(userFrom(rs));
      }
    } catch (SQLException ex) {
      throw authDatabaseError(ex);
    }
  }

  public AuthUserResponse me(String authorization) {
    return resolve(authorization);
  }

  public void logout(String authorization) {
    String token = bearerToken(authorization);
    if (!token.isBlank()) {
      sessions.remove(token);
    }
  }

  public void requireRegistered(String authorization) {
    AuthUserResponse user = resolve(authorization);
    if (!user.authenticated()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "登录后可下载单条数据");
    }
  }

  public AuthUserResponse requireAssistantAccess(String authorization) {
    AuthUserResponse user = resolve(authorization);
    if (!user.authenticated()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "登录后可使用智能助手");
    }
    return user;
  }

  public AuthUserResponse requireDatasetSubmissionAccess(String authorization) {
    AuthUserResponse user = resolve(authorization);
    if (!user.authenticated()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "登录后可提交候选数据集并查看投稿进度");
    }
    return user;
  }

  public void requireAdmin(String authorization) {
    AuthUserResponse user = resolve(authorization);
    if (!user.authenticated()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "登录后可访问下载功能");
    }
    if (!ROLE_ADMIN.equals(user.role()) && !ROLE_SUPER_ADMIN.equals(user.role())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "管理员可下载完整数据集");
    }
  }

  public void requireSuperAdmin(String authorization) {
    AuthUserResponse user = resolve(authorization);
    if (!user.authenticated()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "登录后可访问权限管理");
    }
    if (!ROLE_SUPER_ADMIN.equals(user.role())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "仅超级管理员可管理用户权限");
    }
  }

  public void requireDatasetReviewAccess(String authorization) {
    AuthUserResponse user = resolve(authorization);
    if (!user.authenticated()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "登录后可访问数据接入审核");
    }
    if (!ROLE_SUPER_ADMIN.equals(user.role())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "仅超级管理员可审核并推进数据接入");
    }
  }

  public AuthUserResponse requireQualityReviewAccess(String authorization) {
    AuthUserResponse user = resolve(authorization);
    if (!user.authenticated()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "登录后可访问质量审核明细");
    }
    if (!ROLE_ADMIN.equals(user.role()) && !ROLE_SUPER_ADMIN.equals(user.role())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "仅管理员和超级管理员可访问质量审核明细");
    }
    return user;
  }

  public List<AuthUserResponse> listUsers(String authorization) {
    requireSuperAdmin(authorization);
    List<AuthUserResponse> users = new ArrayList<>();
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(
             """
             SELECT USERNAME, DISPLAY_NAME, ROLE, ACTIVE
             FROM APP_USERS
             ORDER BY CASE ROLE
               WHEN 'SUPER_ADMIN' THEN 0
               WHEN 'ADMIN' THEN 1
               ELSE 2
             END, USERNAME
             """);
         ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        users.add(userFrom(rs));
      }
      return users;
    } catch (SQLException ex) {
      throw authDatabaseError(ex);
    }
  }

  public AuthUserResponse updateRole(String authorization, String usernameValue, String roleValue) {
    requireSuperAdmin(authorization);
    String username = normalizedUsername(usernameValue);
    String role = normalizeAssignableRole(roleValue);
    if ("superadmin".equals(username)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "不能修改内置超级管理员角色");
    }
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement("UPDATE APP_USERS SET ROLE = ? WHERE USERNAME = ?")) {
      statement.setString(1, role);
      statement.setString(2, username);
      if (statement.executeUpdate() == 0) {
        throw new ApiException(HttpStatus.NOT_FOUND, "找不到用户: " + username);
      }
      sessions.entrySet().removeIf(entry -> entry.getValue().username().equals(username));
      return findUser(username);
    } catch (SQLException ex) {
      throw authDatabaseError(ex);
    }
  }

  private AuthSessionResponse createSession(AuthUserResponse user) {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    sessions.put(token, user);
    return new AuthSessionResponse(token, user);
  }

  private AuthUserResponse resolve(String authorization) {
    String token = bearerToken(authorization);
    if (token.isBlank()) {
      return guest();
    }
    return sessions.getOrDefault(token, guest());
  }

  private String bearerToken(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return "";
    }
    return authorization.substring("Bearer ".length()).trim();
  }

  private AuthUserResponse guest() {
    return new AuthUserResponse("", "游客", ROLE_GUEST, false);
  }

  private void seedUser(Connection connection, String username, String displayName, String password, String role)
      throws SQLException {
    String validatedPassword = validatePassword(password);
    try (PreparedStatement query = connection.prepareStatement(
        "SELECT PASSWORD_HASH, ROLE FROM APP_USERS WHERE USERNAME = ?")) {
      query.setString(1, username);
      try (ResultSet rs = query.executeQuery()) {
        if (rs.next()) {
          if (!passwordEncoder.matches(validatedPassword, rs.getString("PASSWORD_HASH"))
              || !role.equals(rs.getString("ROLE"))) {
            try (PreparedStatement update = connection.prepareStatement(
                "UPDATE APP_USERS SET DISPLAY_NAME = ?, PASSWORD_HASH = ?, ROLE = ?, ACTIVE = TRUE WHERE USERNAME = ?")) {
              update.setString(1, displayName);
              update.setString(2, passwordEncoder.encode(validatedPassword));
              update.setString(3, role);
              update.setString(4, username);
              update.executeUpdate();
            }
          }
          return;
        }
      }
    }
    try (PreparedStatement insert = connection.prepareStatement("""
        INSERT INTO APP_USERS (USERNAME, DISPLAY_NAME, PASSWORD_HASH, ROLE, ACTIVE, CREATED_AT)
        VALUES (?, ?, ?, ?, TRUE, ?)
        """)) {
      insert.setString(1, username);
      insert.setString(2, displayName);
      insert.setString(3, passwordEncoder.encode(validatedPassword));
      insert.setString(4, role);
      insert.setString(5, Instant.now().toString());
      insert.executeUpdate();
    }
  }

  private AuthUserResponse findUser(String username) throws SQLException {
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(
             "SELECT USERNAME, DISPLAY_NAME, ROLE, ACTIVE FROM APP_USERS WHERE USERNAME = ?")) {
      statement.setString(1, username);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          throw new ApiException(HttpStatus.NOT_FOUND, "找不到用户: " + username);
        }
        return userFrom(rs);
      }
    }
  }

  private AuthUserResponse userFrom(ResultSet rs) throws SQLException {
    return new AuthUserResponse(
        rs.getString("USERNAME"),
        rs.getString("DISPLAY_NAME"),
        rs.getString("ROLE"),
        rs.getBoolean("ACTIVE")
    );
  }

  private String normalizedUsername(String value) {
    String username = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    if (!username.matches("[a-z0-9][a-z0-9_.-]{2,31}")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "用户名需为 3-32 位字母、数字、点、下划线或短横线");
    }
    return username;
  }

  private String normalizeDisplayName(String value, String fallback) {
    String name = value == null ? "" : value.trim();
    if (name.isBlank()) {
      return fallback;
    }
    if (name.length() > 40) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "显示名称不能超过 40 个字符");
    }
    return name;
  }

  private String validatePassword(String password) {
    if (password == null || password.length() < 8 || password.length() > 100) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "密码长度需为 8-100 位");
    }
    return password;
  }

  private String normalizeAssignableRole(String value) {
    String role = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (!ROLE_ADMIN.equals(role) && !ROLE_USER.equals(role)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "可分配角色仅支持 ADMIN 或 USER");
    }
    return role;
  }

  private Connection openConnection() throws SQLException {
    return DriverManager.getConnection(resolveJdbcUrl(), "sa", "");
  }

  private String resolveJdbcUrl() {
    String cached = jdbcUrl;
    if (cached != null) {
      return cached;
    }
    synchronized (this) {
      if (jdbcUrl == null) {
        Path path = Paths.get(properties.getDbPath());
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate = path.isAbsolute() ? path : cwd.resolve(path).normalize();
        Path projectCandidate = path.isAbsolute() ? path : cwd.resolve("..").resolve(path).normalize();
        if (!path.isAbsolute() && Files.isDirectory(projectCandidate.getParent())) {
          candidate = projectCandidate;
        }
        try {
          Files.createDirectories(candidate.toAbsolutePath().getParent());
        } catch (Exception ex) {
          throw new IllegalStateException("创建认证数据库目录失败: " + ex.getMessage(), ex);
        }
        String normalized = candidate.toAbsolutePath().normalize().toString().replace('\\', '/');
        jdbcUrl = "jdbc:h2:" + normalized;
      }
      return jdbcUrl;
    }
  }

  private static ApiException authDatabaseError(SQLException ex) {
    return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取用户数据库失败: " + ex.getMessage());
  }
}
