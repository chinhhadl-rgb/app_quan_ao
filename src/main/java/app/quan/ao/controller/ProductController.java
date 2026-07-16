package app.quan.ao.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ProductController {

    private final JdbcTemplate jdbcTemplate;

    public ProductController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/products")
    public String products(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Model model
    ) {
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        int offset = (currentPage - 1) * pageSize;

        List<Map<String, Object>> products = jdbcTemplate.queryForList("""
            select p.id, p.name, p.slug, p.base_price, p.sale_price, p.thumbnail,
                   c.name as category_name
            from products p
            left join categories c on p.category_id = c.id
            where p.status = 1
              and (? is null or p.category_id = ?)
              and (? is null or p.brand_id = ?)
              and (? is null or coalesce(p.sale_price, p.base_price) >= ?)
              and (? is null or coalesce(p.sale_price, p.base_price) <= ?)
            order by p.created_at desc
            limit ? offset ?
            """,
            categoryId, categoryId,
            brandId, brandId,
            minPrice, minPrice,
            maxPrice, maxPrice,
            pageSize, offset
        );

        Integer total = jdbcTemplate.queryForObject("""
            select count(*)
            from products p
            where p.status = 1
              and (? is null or p.category_id = ?)
              and (? is null or p.brand_id = ?)
              and (? is null or coalesce(p.sale_price, p.base_price) >= ?)
              and (? is null or coalesce(p.sale_price, p.base_price) <= ?)
            """,
            Integer.class,
            categoryId, categoryId,
            brandId, brandId,
            minPrice, minPrice,
            maxPrice, maxPrice
        );

        for (Map<String, Object> product : products) {
            Integer prodId = (Integer) product.get("id");
            List<Map<String, Object>> variants = jdbcTemplate.queryForList("""
                select id, size, color, stock_quantity, variant_image
                from product_variants
                where product_id = ?
                """, prodId);
            product.put("variants", variants);

            // Tách các màu độc nhất kèm ảnh biến thể tương ứng
            List<Map<String, Object>> uniqueColors = new java.util.ArrayList<>();
            java.util.Set<String> seenColors = new java.util.HashSet<>();
            for (Map<String, Object> v : variants) {
                String color = (String) v.get("color");
                if (color != null && !seenColors.contains(color.trim())) {
                    seenColors.add(color.trim());
                    uniqueColors.add(v);
                }
            }
            product.put("colorVariants", uniqueColors);
        }

        model.addAttribute("products", products);
        
        // Lấy danh mục cha và các danh mục con tương ứng cho menu chính
        List<Map<String, Object>> rootCategories = jdbcTemplate.queryForList(
            "select id, name, slug from categories where parent_id is null and status = 1"
        );
        for (Map<String, Object> cat : rootCategories) {
            Integer catId = (Integer) cat.get("id");
            List<Map<String, Object>> subCats = jdbcTemplate.queryForList(
                "select id, name, slug from categories where parent_id = ? and status = 1", catId
            );
            cat.put("subCategories", subCats);
        }
        model.addAttribute("menuCategories", rootCategories);

        model.addAttribute("categories", jdbcTemplate.queryForList(
            "select id, name from categories where status = 1 order by name"
        ));
        model.addAttribute("brands", jdbcTemplate.queryForList(
            "select id, name from brands where status = 1 order by name"
        ));
        model.addAttribute("page", currentPage);
        model.addAttribute("size", pageSize);
        model.addAttribute("totalPages", (int) Math.ceil((total == null ? 0 : total) * 1.0 / pageSize));
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("brandId", brandId);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        return "client/products";
    }

    @GetMapping("/products/{id}")
    public String detail(@PathVariable int id, Model model) {
        Map<String, Object> product = jdbcTemplate.queryForMap("""
            select p.*, c.name as category_name
            from products p
            left join categories c on p.category_id = c.id
            where p.id = ? and p.status = 1
            """, id);

        List<Map<String, Object>> variants = jdbcTemplate.queryForList("""
            select id, size, color, stock_quantity, variant_image
            from product_variants
            where product_id = ?
            order by size, color
            """, id);

        model.addAttribute("product", product);
        model.addAttribute("variants", variants);
        return "client/product-detail";
    }
}
