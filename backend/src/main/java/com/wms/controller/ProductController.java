package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.ProductDTO;
import com.wms.service.EquipmentService;
import com.wms.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;
    private final EquipmentService equipmentService;

    public ProductController(ProductService productService, EquipmentService equipmentService) {
        this.productService = productService;
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public Result<Page<ProductDTO>> page(
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(productService.page(sku, name, page, size));
    }

    @GetMapping("/{id}")
    public Result<ProductDTO> get(@PathVariable Long id) {
        return Result.ok(productService.getById(id));
    }

    /** 编码前缀 → 下一个可用完整编码：新增货品人工只填前两段，流水号系统生成 */
    @GetMapping("/next-sku")
    public Result<String> nextSku(@RequestParam String prefix) {
        return Result.ok(productService.nextSku(prefix));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('master:edit')")
    public Result<ProductDTO> create(@Valid @RequestBody ProductDTO dto) {
        return Result.ok(productService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('master:edit')")
    public Result<ProductDTO> update(@PathVariable Long id, @RequestBody ProductDTO dto) {
        return Result.ok(productService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('master:edit')")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.ok();
    }

    /** 已关联设备数：关联设备弹框顶部展示 */
    @GetMapping("/{id}/equipments")
    public Result<Long> boundEquipmentCount(@PathVariable Long id) {
        return Result.ok(equipmentService.boundEquipmentCount(id));
    }

    /** 批量关联设备：一个备件一次绑到多台设备（数据初始化场景）。 */
    @PostMapping("/{id}/equipments")
    @PreAuthorize("hasAuthority('master:edit')")
    public Result<Map<String, Object>> bindEquipments(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        return Result.ok(equipmentService.bindPartBulk(id, req));
    }
}
