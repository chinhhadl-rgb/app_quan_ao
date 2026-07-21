package app.quan.ao.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import java.util.Map;

@Controller
public class OrderStatusController {

    private final JdbcTemplate jdbcTemplate;

    public OrderStatusController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/my-orders")
    public String orderStatus(HttpSession session, Model model) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        List<Map<String, Object>> orders = jdbcTemplate.queryForList("""
            select * from orders 
            where user_id = ? 
            order by order_date desc
            """, userId);
            
        for (Map<String, Object> order : orders) {
            Integer orderId = (Integer) order.get("id");
            List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                select od.*, p.name as product_name, p.thumbnail, pv.size
                from order_details od
                join product_variants pv on od.variant_id = pv.id
                join products p on pv.product_id = p.id
                where od.order_id = ?
                """, orderId);
            order.put("items", items);
        }

        model.addAttribute("orders", orders);
        return "client/my-orders";
    }
}
