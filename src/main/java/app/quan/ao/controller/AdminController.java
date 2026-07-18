package app.quan.ao.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminController {

    private final JdbcTemplate jdbcTemplate;

    public AdminController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/admin")
    public String dashboard(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        model.addAttribute("stats", jdbcTemplate.queryForList("""
            select date(order_date) as order_day,
                   count(*) as total_orders,
                   coalesce(sum(total_final), 0) as revenue
            from orders
            where order_status = 'Delivered'
            group by date(order_date)
            order by order_day desc
            limit 10
            """));
        return "admin/dashboard";
    }

    @GetMapping("/admin/products")
    public String products(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        model.addAttribute("products", jdbcTemplate.queryForList("""
            from products p
            left join categories c on p.category_id = c.id
            order by p.id desc
            """));
        model.addAttribute("categories", jdbcTemplate.queryForList("select id, name from categories order by name"));
        return "admin/products";
    }

    @PostMapping("/admin/products/save")
    public String saveProduct(
            @RequestParam(required = false) Integer id,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam String name,
            @RequestParam String slug,
            @RequestParam String basePrice,
            @RequestParam(required = false) String salePrice,
            @RequestParam String thumbnail,
            @RequestParam(required = false) String description,
            HttpSession session
    ) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        if (id == null) {
            jdbcTemplate.update("""
        } else {
            jdbcTemplate.update("""
                update products
                    thumbnail = ?, description = ?
                where id = ?
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/admin/products/delete")
    public String deleteProduct(@RequestParam int id, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        jdbcTemplate.update("update products set status = 0 where id = ?", id);
        return "redirect:/admin/products";
    }

    @GetMapping("/admin/orders")
    public String orders(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

            select o.*, u.username
            from orders o
            left join users u on o.user_id = u.id
            order by o.order_date desc
            """));
    }

    @PostMapping("/admin/orders/status")
    public String updateOrderStatus(@RequestParam int id, @RequestParam String status, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        jdbcTemplate.update("update orders set order_status = ? where id = ?", status, id);
        return "redirect:/admin/orders";
    }

    private boolean isAdmin(HttpSession session) {
        Object role = session.getAttribute("role");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
