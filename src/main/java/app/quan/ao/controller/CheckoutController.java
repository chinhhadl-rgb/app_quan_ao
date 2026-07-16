package app.quan.ao.controller;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CheckoutController {

    private final JdbcTemplate jdbcTemplate;

    public CheckoutController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/checkout")
    public String checkoutPage(HttpSession session, Model model) {
        Integer userId = currentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        Map<String, Object> user = jdbcTemplate.queryForMap(
            "select fullname, phone, address from users where id = ?",
            userId
        );
        BigDecimal totalRaw = cartTotal(userId);
        BigDecimal shippingFee = new BigDecimal("30000");

        model.addAttribute("user", user);
        model.addAttribute("totalRaw", totalRaw);
        model.addAttribute("shippingFee", shippingFee);
        model.addAttribute("totalFinal", totalRaw.add(shippingFee));
        return "client/checkout";
    }

    @Transactional
    @PostMapping("/checkout")
    public String createOrder(
            @RequestParam String fullname,
            @RequestParam String phone,
            @RequestParam String address,
            @RequestParam(required = false) String note,
            HttpSession session,
            Model model
    ) {
        Integer userId = currentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        BigDecimal totalRaw = cartTotal(userId);
        if (totalRaw.compareTo(BigDecimal.ZERO) <= 0) {
            model.addAttribute("error", "Gio hang dang trong.");
            return "client/checkout";
        }

        BigDecimal shippingFee = new BigDecimal("30000");
        BigDecimal totalFinal = totalRaw.add(shippingFee);
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                insert into orders
                (user_id, shipping_fullname, shipping_phone, shipping_address,
                 total_raw, shipping_fee, total_final, payment_method, payment_status, order_status, note)
                values (?, ?, ?, ?, ?, ?, ?, 'COD', 'Unpaid', 'Pending', ?)
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, userId);
            ps.setString(2, fullname);
            ps.setString(3, phone);
            ps.setString(4, address);
            ps.setBigDecimal(5, totalRaw);
            ps.setBigDecimal(6, shippingFee);
            ps.setBigDecimal(7, totalFinal);
            ps.setString(8, note);
            return ps;
        }, keyHolder);

        int orderId = keyHolder.getKey().intValue();

        jdbcTemplate.update("""
            insert into order_details (order_id, variant_id, price, quantity)
            select ?, c.variant_id, coalesce(p.sale_price, p.base_price), c.quantity
            from cart c
            join product_variants pv on c.variant_id = pv.id
            join products p on pv.product_id = p.id
            where c.user_id = ?
            """, orderId, userId);

        jdbcTemplate.update("delete from cart where user_id = ?", userId);
        return "redirect:/orders/success?orderId=" + orderId;
    }

    @GetMapping("/orders/success")
    public String success(@RequestParam int orderId, Model model) {
        model.addAttribute("orderId", orderId);
        return "client/order-success";
    }

    private BigDecimal cartTotal(int userId) {
        return jdbcTemplate.queryForObject("""
            select coalesce(sum(coalesce(p.sale_price, p.base_price) * c.quantity), 0)
            from cart c
            join product_variants pv on c.variant_id = pv.id
            join products p on pv.product_id = p.id
            where c.user_id = ?
            """, BigDecimal.class, userId);
    }

    private Integer currentUserId(HttpSession session) {
        Object userId = session.getAttribute("userId");
        return userId == null ? null : Integer.parseInt(userId.toString());
    }
}
