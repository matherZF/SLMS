package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.ProductDTO;
import com.wms.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
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
}
