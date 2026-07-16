package app.quan.ao.controller;

import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void seedAdminUser() {
        try {
            // Sử dụng INSERT IGNORE để tránh lỗi trùng lặp
            jdbcTemplate.update("insert ignore into roles (id, role_name, description) values (1, 'ROLE_ADMIN', 'Administrator')");
            jdbcTemplate.update("insert ignore into roles (id, role_name, description) values (2, 'ROLE_STAFF', 'Staff')");
            jdbcTemplate.update("insert ignore into roles (id, role_name, description) values (3, 'ROLE_USER', 'Customer')");
            
            // Lấy ID của role admin bằng queryForList để tránh lỗi "expected 1, actual 0"
            List<Integer> ids = jdbcTemplate.queryForList("select id from roles where role_name = 'ROLE_ADMIN' limit 1", Integer.class);
            
            if (ids.isEmpty()) {
                System.err.println("Cảnh báo: Không tìm thấy Role 'ROLE_ADMIN' trong database.");
                return;
            }

            Integer adminRoleId = ids.get(0);

            // Kiểm tra và tạo tài khoản admin nếu chưa có
            Integer adminUserCount = jdbcTemplate.queryForObject(
                "select count(*) from users where username = 'chinhha_admin'", Integer.class);
            if (adminUserCount == null || adminUserCount == 0) {
                String encodedPassword = passwordEncoder.encode("123");
                jdbcTemplate.update("""
                    insert into users (role_id, username, password, fullname, email, status)
                    values (?, 'chinhha_admin', ?, 'Chinhha Admin', 'admin@chinhfashion.com', 1)
                    """, adminRoleId, encodedPassword);
            }
        } catch (Exception e) {
            System.err.println("Lỗi tự động tạo tài khoản admin: " + e.getMessage());
        }
    }

    @GetMapping("/login")
    public String loginPage() {
        return "client/login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam String username,
            @RequestParam String password,
            HttpSession session,
            Model model
    ) {
        List<Map<String, Object>> users = jdbcTemplate.queryForList("""
            select u.id, u.username, u.password, u.fullname, r.role_name
            from users u
            join roles r on u.role_id = r.id
            where u.username = ? and u.status = 1
            """, username);

        if (users.isEmpty()) {
            model.addAttribute("error", "Sai tai khoan hoac mat khau.");
            return "client/login";
        }

        Map<String, Object> user = users.get(0);
        String savedPassword = user.get("password").toString();
        boolean passwordMatches = savedPassword.startsWith("$2")
            ? passwordEncoder.matches(password, savedPassword)
            : password.equals(savedPassword);

        if (!passwordMatches) {
            model.addAttribute("error", "Sai tai khoan hoac mat khau.");
            return "client/login";
        }

        session.setAttribute("userId", user.get("id"));
        session.setAttribute("username", user.get("username"));
        session.setAttribute("fullname", user.get("fullname"));
        session.setAttribute("role", user.get("role_name"));
        return "redirect:/products";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "client/register";
    }

    @PostMapping("/register")
    public String register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String fullname,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String address,
            Model model
    ) {
        try {
            jdbcTemplate.update("""
                insert into users (role_id, username, password, fullname, email, phone, address)
                values (3, ?, ?, ?, ?, ?, ?)
                """,
                username,
                passwordEncoder.encode(password),
                fullname,
                blankToNull(email),
                blankToNull(phone),
                blankToNull(address)
            );
        } catch (DuplicateKeyException e) {
            model.addAttribute("error", "Username hoac email da ton tai.");
            return "client/register";
        }

        return "redirect:/login";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
