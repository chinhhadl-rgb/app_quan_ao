package app.quan.ao.controller;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
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
    public String checkoutPage(
            @RequestParam(required = false) List<Integer> cartIds,
            HttpSession session, 
            Model model
    ) {
        Integer userId = currentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        if (cartIds == null || cartIds.isEmpty()) {
            return "redirect:/cart";
        }

        Map<String, Object> user = jdbcTemplate.queryForMap(
            "select fullname, phone, address from users where id = ?",
            userId
        );

        // Lấy danh sách các item được chọn
        String inSql = cartIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).get();
        List<Map<String, Object>> selectedItems = jdbcTemplate.queryForList(String.format("""
            select c.id as cart_id, c.quantity, pv.id as variant_id,
                   p.name, coalesce(p.sale_price, p.base_price) as price
            from cart c
            join product_variants pv on c.variant_id = pv.id
            join products p on pv.product_id = p.id
            where c.user_id = ? and c.id in (%s)
            """, inSql), userId);

        BigDecimal totalRaw = BigDecimal.ZERO;
        for (Map<String, Object> item : selectedItems) {
            BigDecimal price = toBigDecimal(item.get("price"));
            int qty = toInt(item.get("quantity"));
            totalRaw = totalRaw.add(price.multiply(new BigDecimal(qty)));
        }

        BigDecimal shippingFee = new BigDecimal("30000");

        model.addAttribute("user", user);
        model.addAttribute("totalRaw", totalRaw);
        model.addAttribute("shippingFee", shippingFee);
        model.addAttribute("totalFinal", totalRaw.add(shippingFee));
        model.addAttribute("cartIds", inSql);
        return "client/checkout";
    }

    @Transactional
    @PostMapping("/checkout")
    public String createOrder(
            @RequestParam String fullname,
            @RequestParam String phone,
            @RequestParam String address,
            @RequestParam String cartIds,
            @RequestParam(required = false) String note,
            HttpSession session,
            Model model
    ) {
        Integer userId = currentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        List<Map<String, Object>> selectedItems = jdbcTemplate.queryForList(String.format("""
            select c.variant_id, coalesce(p.sale_price, p.base_price) as price, c.quantity
            from cart c
            join product_variants pv on c.variant_id = pv.id
            join products p on pv.product_id = p.id
            where c.user_id = ? and c.id in (%s)
            """, cartIds), userId);

        if (selectedItems.isEmpty()) {
            return "redirect:/cart";
        }

        BigDecimal totalRaw = BigDecimal.ZERO;
        for (Map<String, Object> item : selectedItems) {
            BigDecimal price = toBigDecimal(item.get("price"));
            int qty = toInt(item.get("quantity"));
            totalRaw = totalRaw.add(price.multiply(new BigDecimal(qty)));
        }

        BigDecimal shippingFee = new BigDecimal("30000");
        BigDecimal totalFinal = totalRaw.add(shippingFee);
        KeyHolder keyHolder = new GeneratedKeyHolder();

        final BigDecimal finalTotalRaw = totalRaw;
        final BigDecimal finalShippingFee = shippingFee;
        final BigDecimal finalTotalFinal = totalFinal;

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
            ps.setBigDecimal(5, finalTotalRaw);
            ps.setBigDecimal(6, finalShippingFee);
            ps.setBigDecimal(7, finalTotalFinal);
            ps.setString(8, note);
            return ps;
        }, keyHolder);

        int orderId = keyHolder.getKey().intValue();

        for (Map<String, Object> item : selectedItems) {
            jdbcTemplate.update("""
                insert into order_details (order_id, variant_id, price, quantity)
                values (?, ?, ?, ?)
                """, orderId, item.get("variant_id"), toBigDecimal(item.get("price")), toInt(item.get("quantity")));
        }

        jdbcTemplate.update(String.format("delete from cart where user_id = ? and id in (%s)", cartIds), userId);
        
        return "redirect:/orders/success?orderId=" + orderId;
    }

    @GetMapping("/orders/success")
    public String success(@RequestParam int orderId, Model model) {
        model.addAttribute("orderId", orderId);
        return "client/order-success";
    }

    private Integer currentUserId(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) return null;
        try {
            return Integer.parseInt(userId.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        return new BigDecimal(val.toString());
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        return Integer.parseInt(val.toString());
    }
}
