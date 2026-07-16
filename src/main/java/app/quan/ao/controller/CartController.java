package app.quan.ao.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CartController {

    private final JdbcTemplate jdbcTemplate;

    public CartController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/cart")
    public String cart(HttpSession session, Model model) {
        Integer userId = currentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
            select c.id as cart_id, c.quantity, pv.id as variant_id, pv.size, pv.color,
                   p.id as product_id, p.name, p.thumbnail,
                   coalesce(p.sale_price, p.base_price) as price,
                   coalesce(p.sale_price, p.base_price) * c.quantity as line_total
            from cart c
            join product_variants pv on c.variant_id = pv.id
            join products p on pv.product_id = p.id
            where c.user_id = ?
            order by c.updated_at desc
            """, userId);

        BigDecimal total = jdbcTemplate.queryForObject("""
            select coalesce(sum(coalesce(p.sale_price, p.base_price) * c.quantity), 0)
            from cart c
            join product_variants pv on c.variant_id = pv.id
            join products p on pv.product_id = p.id
            where c.user_id = ?
            """, BigDecimal.class, userId);

        model.addAttribute("items", items);
        model.addAttribute("total", total);
        return "client/cart";
    }

    @PostMapping("/cart/add")
    public String add(@RequestParam int variantId, @RequestParam(defaultValue = "1") int quantity, HttpSession session) {
        Integer userId = currentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from cart where user_id = ? and variant_id = ?",
            Integer.class,
            userId,
            variantId
        );

        if (count != null && count > 0) {
            jdbcTemplate.update("""
                update cart
                set quantity = quantity + ?, updated_at = current_timestamp
                where user_id = ? and variant_id = ?
                """, Math.max(quantity, 1), userId, variantId);
        } else {
            jdbcTemplate.update(
                "insert into cart (user_id, variant_id, quantity) values (?, ?, ?)",
                userId,
                variantId,
                Math.max(quantity, 1)
            );
        }

        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String update(@RequestParam int cartId, @RequestParam int quantity, HttpSession session) {
        Integer userId = currentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        if (quantity <= 0) {
            jdbcTemplate.update("delete from cart where id = ? and user_id = ?", cartId, userId);
        } else {
            jdbcTemplate.update("update cart set quantity = ? where id = ? and user_id = ?", quantity, cartId, userId);
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String remove(@RequestParam int cartId, HttpSession session) {
        Integer userId = currentUserId(session);
        if (userId != null) {
            jdbcTemplate.update("delete from cart where id = ? and user_id = ?", cartId, userId);
        }
        return "redirect:/cart";
    }

    private Integer currentUserId(HttpSession session) {
        Object userId = session.getAttribute("userId");
        return userId == null ? null : Integer.parseInt(userId.toString());
    }
}
