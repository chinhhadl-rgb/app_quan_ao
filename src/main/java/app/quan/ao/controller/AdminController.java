package app.quan.ao.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.Map;

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
            select p.*, c.name as category_name, b.name as brand_name
            from products p
            left join categories c on p.category_id = c.id
            left join brands b on p.brand_id = b.id
            order by p.id desc
            """));
        model.addAttribute("categories", jdbcTemplate.queryForList("select id, name from categories order by name"));
        model.addAttribute("brands", jdbcTemplate.queryForList("select id, name from brands order by name"));
        return "admin/products";
    }

    @PostMapping("/admin/products/save")
    public String saveProduct(
            @RequestParam(required = false) Integer id,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer brandId,
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
                insert into products (category_id, brand_id, name, slug, base_price, sale_price, thumbnail, description)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, categoryId, brandId, name, slug, basePrice, blankToNull(salePrice), thumbnail, description);
        } else {
            jdbcTemplate.update("""
                update products
                set category_id = ?, brand_id = ?, name = ?, slug = ?, base_price = ?, sale_price = ?,
                    thumbnail = ?, description = ?
                where id = ?
                """, categoryId, brandId, name, slug, basePrice, blankToNull(salePrice), thumbnail, description, id);
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

        List<Map<String, Object>> orders = jdbcTemplate.queryForList("""
            select o.*, u.username
            from orders o
            left join users u on o.user_id = u.id
            order by o.order_date desc
            """);
        
        for (Map<String, Object> order : orders) {
            Integer orderId = (Integer) order.get("id");
            List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                select od.id as detail_id, od.quantity, od.price, p.name as product_name, pv.size, pv.color
                from order_details od
                join product_variants pv on od.variant_id = pv.id
                join products p on pv.product_id = p.id
                where od.order_id = ?
                """, orderId);
            order.put("items", items);
        }

        model.addAttribute("orders", orders);
        return "admin/orders";
    }

    @PostMapping("/admin/orders/item/quantity")
    public String updateOrderItemQuantity(
            @RequestParam int orderId,
            @RequestParam int detailId,
            @RequestParam int quantity,
            HttpSession session
    ) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        // Cập nhật số lượng trong chi tiết đơn hàng
        jdbcTemplate.update("update order_details set quantity = ? where id = ?", quantity, detailId);

        // Tính toán lại tổng tiền của đơn hàng
        jdbcTemplate.update("""
            update orders o
            set total_final = (
                select sum(quantity * price)
                from order_details
                where order_id = o.id
            )
            where id = ?
            """, orderId);

        return "redirect:/admin/orders";
    }

    @GetMapping("/admin/users")
    public String users(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        model.addAttribute("users", jdbcTemplate.queryForList("""
            select u.*, r.role_name 
            from users u 
            join roles r on u.role_id = r.id
            order by u.id desc
            """));
        model.addAttribute("roles", jdbcTemplate.queryForList("select * from roles"));
        return "admin/users";
    }

    @PostMapping("/admin/users/role")
    public String updateUserRole(@RequestParam int userId, @RequestParam int roleId, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        jdbcTemplate.update("update users set role_id = ? where id = ?", roleId, userId);
        return "redirect:/admin/users";
    }

    @GetMapping("/admin/categories")
    public String categories(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        model.addAttribute("categories", jdbcTemplate.queryForList("select * from categories order by id desc"));
        return "admin/categories";
    }

    @PostMapping("/admin/categories/save")
    public String saveCategory(@RequestParam(required = false) Integer id, @RequestParam String name, @RequestParam String slug, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        if (id == null) {
            jdbcTemplate.update("insert into categories (name, slug) values (?, ?)", name, slug);
        } else {
            jdbcTemplate.update("update categories set name = ?, slug = ? where id = ?", name, slug, id);
        }
        return "redirect:/admin/categories";
    }

    @GetMapping("/admin/brands")
    public String brands(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        model.addAttribute("brands", jdbcTemplate.queryForList("select * from brands order by id desc"));
        return "admin/brands";
    }

    @GetMapping("/admin/brands/{id}/products")
    public String brandProducts(@PathVariable int id, HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        Map<String, Object> brand = jdbcTemplate.queryForMap("select * from brands where id = ?", id);
        List<Map<String, Object>> products = jdbcTemplate.queryForList("""
            select p.*, c.name as category_name 
            from products p 
            left join categories c on p.category_id = c.id 
            where p.brand_id = ?
            """, id);
        
        model.addAttribute("brand", brand);
        model.addAttribute("products", products);
        return "admin/brand-products";
    }

    @GetMapping("/admin/products/edit/{id}")
    public String editProductPage(@PathVariable int id, HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        Map<String, Object> product = jdbcTemplate.queryForMap("select * from products where id = ?", id);
        List<Map<String, Object>> variants = jdbcTemplate.queryForList("select * from product_variants where product_id = ?", id);
        
        model.addAttribute("product", product);
        model.addAttribute("variants", variants);
        model.addAttribute("categories", jdbcTemplate.queryForList("select * from categories order by name"));
        model.addAttribute("brands", jdbcTemplate.queryForList("select * from brands order by name"));
        return "admin/product-edit";
    }

    @PostMapping("/admin/products/variant/save")
    public String saveVariant(
            @RequestParam int productId,
            @RequestParam(required = false) Integer variantId,
            @RequestParam String size,
            @RequestParam String color,
            @RequestParam int stock,
            HttpSession session
    ) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        if (variantId == null) {
            jdbcTemplate.update(
                "insert into product_variants (product_id, size, color, stock_quantity) values (?, ?, ?, ?)",
                productId, size, color, stock
            );
        } else {
            jdbcTemplate.update(
                "update product_variants set size = ?, color = ?, stock_quantity = ? where id = ?",
                size, color, stock, variantId
            );
        }
        return "redirect:/admin/products/edit/" + productId;
    }

    @PostMapping("/admin/products/variant/delete")
    public String deleteVariant(@RequestParam int variantId, @RequestParam int productId, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        jdbcTemplate.update("delete from product_variants where id = ?", variantId);
        return "redirect:/admin/products/edit/" + productId;
    }

    @PostMapping("/admin/brands/save")
    public String saveBrand(@RequestParam(required = false) Integer id, @RequestParam String name, @RequestParam String slug, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        if (id == null) {
            jdbcTemplate.update("insert into brands (name, slug) values (?, ?)", name, slug);
        } else {
            jdbcTemplate.update("update brands set name = ?, slug = ? where id = ?", name, slug, id);
        }
        return "redirect:/admin/brands";
    }

    @GetMapping("/admin/revenue")
    public String revenueReport(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        
        // Thống kê doanh thu theo tháng trong năm nay
        model.addAttribute("monthlyRevenue", jdbcTemplate.queryForList("""
            select month(order_date) as month,
                   sum(total_final) as total_revenue,
                   count(*) as total_orders
            from orders
            where order_status = 'Delivered' and year(order_date) = year(curdate())
            group by month(order_date)
            order by month
            """));
            
        // Thống kê doanh thu theo thương hiệu
        model.addAttribute("brandRevenue", jdbcTemplate.queryForList("""
            select b.name as brand_name,
                   sum(od.price * od.quantity) as revenue
            from order_details od
            join product_variants pv on od.variant_id = pv.id
            join products p on pv.product_id = p.id
            join brands b on p.brand_id = b.id
            join orders o on od.order_id = o.id
            where o.order_status = 'Delivered'
            group by b.name
            order by revenue desc
            """));

        return "admin/revenue";
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
        return "ROLE_ADMIN".equals(role) || "ROLE_STAFF".equals(role);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
