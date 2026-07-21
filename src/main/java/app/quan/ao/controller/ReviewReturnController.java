package app.quan.ao.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Map;

@Controller
public class ReviewReturnController {

    private final JdbcTemplate jdbcTemplate;

    public ReviewReturnController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/reviews/add")
    public String addReview(
            @RequestParam int orderId,
            @RequestParam int productId,
            @RequestParam int rating,
            @RequestParam String comment,
            HttpSession session
    ) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";

        // Kiểm tra xem đơn hàng đã giao chưa
        Map<String, Object> order = jdbcTemplate.queryForMap("select order_status from orders where id = ?", orderId);
        if (!"Delivered".equals(order.get("order_status"))) {
            return "redirect:/my-orders?error=not_delivered";
        }

        jdbcTemplate.update("""
            insert into reviews (user_id, product_id, order_id, rating, comment)
            values (?, ?, ?, ?, ?)
            """, userId, productId, orderId, rating, comment);

        return "redirect:/my-orders?success=reviewed";
    }

    @PostMapping("/returns/request")
    public String requestReturn(
            @RequestParam int orderId,
            @RequestParam String reason,
            HttpSession session
    ) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";

        jdbcTemplate.update("""
            insert into returns (user_id, order_id, reason, status)
            values (?, ?, ?, 'Pending')
            """, userId, orderId, reason);

        return "redirect:/my-orders?success=return_requested";
    }
}
