package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.DataDictionaryDTO;
import com.wms.service.DataDictionaryService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/dictionaries")
public class DataDictionaryController {
    private final DataDictionaryService service;
    public DataDictionaryController(DataDictionaryService service){this.service=service;}
    @GetMapping("/options/{type}") public Result<List<DataDictionaryDTO>> options(@PathVariable String type){return Result.ok(service.options(type));}
    @GetMapping @PreAuthorize("hasAuthority('system:dict')") public Result<Page<DataDictionaryDTO>> page(@RequestParam(required=false)String type,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return Result.ok(service.page(type,page,size));}
    @PostMapping @PreAuthorize("hasAuthority('system:dict')") public Result<DataDictionaryDTO> create(@RequestBody DataDictionaryDTO dto){return Result.ok(service.create(dto));}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('system:dict')") public Result<DataDictionaryDTO> update(@PathVariable Long id,@RequestBody DataDictionaryDTO dto){return Result.ok(service.update(id,dto));}
}
